package com.xhhao.com.releasehub.account;

import com.xhhao.com.releasehub.model.HubStore;
import com.xhhao.com.releasehub.model.Project;
import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.provider.ProviderHttp;
import com.xhhao.com.releasehub.security.SecretStore;
import com.xhhao.com.releasehub.security.SecretAccess;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AccountService {
    private final HubStore store;
    private final SecretStore secrets;
    private final SecretAccess secretAccess;
    private final Map<String, RepositoryCatalog> catalogs;
    public AccountService(HubStore store, SecretStore secrets, SecretAccess secretAccess, List<RepositoryCatalog> catalogs) {
        this.store = store; this.secrets = secrets; this.secretAccess = secretAccess;
        this.catalogs = catalogs.stream().collect(Collectors.toMap(RepositoryCatalog::name, Function.identity()));
    }

    public record AccountInput(String name, String displayName, String provider, String instanceUrl, String credentialSecret) { }
    public record AccountView(String name, String displayName, String provider, String instanceUrl,
                              String username, boolean hasToken, String credentialSecret, Instant verifiedAt, long linkedProjects) { }
    public record RepositoryCandidate(String path, String displayName, String description, String url,
                                      boolean privateRepository, boolean archived, boolean added) { }
    public record RepositoryPageView(List<RepositoryCandidate> items, int page, boolean hasMore) { }

    public RepositoryCatalog catalog(RepositoryAccount account) {
        var catalog = catalogs.get(account.getSpec().getProvider());
        if (catalog == null) throw new IllegalArgumentException("该平台暂不支持仓库查询");
        return catalog;
    }
    public Mono<RepositoryAccount> required(String id) {
        return store.client.fetch(RepositoryAccount.class, id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("仓库账号不存在")));
    }
    public Mono<String> token(RepositoryAccount account) {
        return secrets.token(account.getSpec().getCredentialSecret());
    }
    public Mono<List<AccountView>> list() {
        return Mono.zip(store.all(RepositoryAccount.class).collectList(), store.all(Project.class).collectList())
            .map(t -> t.getT1().stream().sorted(Comparator.comparing(a -> a.getSpec().getDisplayName()))
                .map(a -> view(a, t.getT2().stream().filter(p -> a.getMetadata().getName().equals(p.getSpec().getAccountName())).count())).toList());
    }
    public Mono<AccountView> save(AccountInput input) {
        return Mono.defer(() -> {
            if (input.displayName() == null || input.displayName().isBlank() || input.displayName().length() > 100)
                return Mono.error(new IllegalArgumentException("请填写 1–100 字的账号名称"));
            String instance = AccountAddress.normalize(input.provider(), input.instanceUrl());
            boolean creating = input.name() == null || input.name().isBlank();
            Mono<RepositoryAccount> account = creating ? Mono.just(fresh(input, instance)) : required(input.name());
            return account.flatMap(a -> {
                if (!input.provider().equals(a.getSpec().getProvider()) || !instance.equals(a.getSpec().getInstanceUrl()))
                    return Mono.error(new IllegalArgumentException("已有账号的平台与站点不能更换"));
                String supplied = input.credentialSecret() == null ? "" : input.credentialSecret().strip();
                String reference = supplied.isBlank() && !creating ? a.getSpec().getCredentialSecret() : supplied;
                if (reference == null || reference.isBlank()) return Mono.error(new IllegalArgumentException("请选择包含 token 字段的 Secret"));
                boolean changed = creating || !reference.equals(a.getSpec().getCredentialSecret());
                Mono<Void> access = changed ? secretAccess.requireRead(reference) : Mono.empty();
                Mono<String> value = access.then(secrets.token(reference));
                return value.flatMap(token -> catalog(a).identity(a.getSpec(), token).onErrorMap(AccountService::platformError)
                    .flatMap(identity -> {
                        if (!creating && !identity.id().equals(a.getStatus().getRemoteId()))
                            return Mono.error(new IllegalArgumentException("新 Token 属于其他账号，请另建仓库账号"));
                        String id = creating ? HubStore.id("account", input.provider() + ":" + instance + ":" + identity.id()) : input.name();
                        Mono<Boolean> duplicate = creating ? store.client.fetch(RepositoryAccount.class, id).hasElement() : Mono.just(false);
                        return duplicate.flatMap(exists -> {
                            if (exists) return Mono.error(new IllegalArgumentException("该平台账号已添加，请编辑现有账号"));
                            if (creating) a.setMetadata(HubStore.metadata(id));
                            a.getSpec().setDisplayName(input.displayName().strip());
                            a.getStatus().setRemoteId(identity.id()); a.getStatus().setUsername(identity.username());
                            a.getStatus().setVerifiedAt(Instant.now());
                            a.getSpec().setCredentialSecret(reference);
                            return creating ? store.client.create(a) : store.client.update(a);
                        });
                    }));
            }).flatMap(a -> store.all(Project.class).filter(p -> a.getMetadata().getName().equals(p.getSpec().getAccountName()))
                .count().map(count -> view(a, count)));
        });
    }
    public Mono<RepositoryPageView> repositories(String id, int page) {
        if (page < 1 || page > 1000) return Mono.error(new IllegalArgumentException("仓库分页超出范围"));
        return required(id).flatMap(a -> Mono.zip(token(a).flatMap(token -> catalog(a).repositories(a.getSpec(), token, page))
            .onErrorMap(AccountService::platformError), store.all(Project.class).collectList()).map(t -> {
                var added = t.getT2().stream().filter(p -> p.getSpec().getProvider().equals(a.getSpec().getProvider()))
                    .map(p -> AccountAddress.key(p.getSpec().getProvider(), p.getSpec().getRepositoryUrl())).collect(Collectors.toSet());
                return new RepositoryPageView(t.getT1().items().stream().map(r -> new RepositoryCandidate(r.path(), r.displayName(),
                    r.description(), r.url(), r.privateRepository(), r.archived(), added.contains(AccountAddress.key(a.getSpec().getProvider(), r.url()))))
                    .toList(), page, t.getT1().hasMore());
            }));
    }
    public Mono<Void> delete(String id) {
        return required(id).flatMap(a -> store.all(Project.class).any(p -> id.equals(p.getSpec().getAccountName())).flatMap(used -> {
            if (used) return Mono.error(new IllegalArgumentException("该账号仍有关联仓库，请先在仓库编辑中解除关联"));
            return store.client.delete(a).then();
        }));
    }
    private RepositoryAccount fresh(AccountInput input, String instance) {
        var a = new RepositoryAccount(); var spec = new RepositoryAccount.Spec();
        spec.setProvider(input.provider()); spec.setInstanceUrl(instance); a.setSpec(spec); return a;
    }
    private AccountView view(RepositoryAccount a, long count) {
        var spec = a.getSpec(); var status = a.getStatus();
        return new AccountView(a.getMetadata().getName(), spec.getDisplayName(), spec.getProvider(), spec.getInstanceUrl(),
            status.getUsername(), spec.getCredentialSecret() != null && !spec.getCredentialSecret().isBlank(), spec.getCredentialSecret(), status.getVerifiedAt(), count);
    }
    public static IllegalArgumentException platformError(Throwable error) {
        if (error instanceof ProviderHttp.ProviderFailure failure) {
            return new IllegalArgumentException(switch (failure.status) {
                case 401 -> "Token 无效或已过期，请重新填写";
                case 403 -> "平台拒绝访问，请检查 Token 权限、组织授权或 API 配额";
                case 404 -> "平台上找不到该仓库，或 Token 无权访问";
                case 429 -> "平台 API 已限流，请稍后再试";
                default -> "平台 API 返回 HTTP " + failure.status + "，请稍后再试";
            });
        }
        // Outbound exceptions and JSON errors can contain credentials or raw platform responses.
        return new IllegalArgumentException("无法查询平台，请检查 Token 权限、网络与 GitHub API 代理设置");
    }
}
