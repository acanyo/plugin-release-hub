package com.xhhao.com.releasehub.account;

import com.xhhao.com.releasehub.model.HubStore;
import com.xhhao.com.releasehub.model.Project;
import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.provider.RepositoryAddress;
import com.xhhao.com.releasehub.service.ProjectService;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AccountImportService {
    private final AccountService accounts;
    private final ProjectService projects;
    private final HubStore store;
    public AccountImportService(AccountService accounts, ProjectService projects, HubStore store) {
        this.accounts = accounts; this.projects = projects; this.store = store;
    }
    public record RepositoryImportInput(List<String> repositoryUrls, int syncMinutes, boolean enabled) { }
    public record RepositoryImportItem(String url, String projectName, String state, String message) { }
    public record RepositoryImportResult(List<RepositoryImportItem> items) { }

    public Mono<RepositoryImportResult> importRepositories(String accountName, RepositoryImportInput input) {
        if (input.repositoryUrls() == null || input.repositoryUrls().isEmpty() || input.repositoryUrls().size() > 50
            || input.repositoryUrls().stream().anyMatch(url -> url == null || url.isBlank() || url.length() > 2000)
            || input.syncMinutes() < 5 || input.syncMinutes() > 10080)
            return Mono.error(new IllegalArgumentException("每批请选择 1–50 个仓库，同步间隔需为 5–10080 分钟"));
        return accounts.required(accountName).flatMap(account -> accounts.token(account).flatMap(token ->
            Flux.fromIterable(input.repositoryUrls()).distinct().flatMapSequential(url -> Mono.defer(() -> importOne(account, token, url, input))
                .onErrorResume(error -> Mono.just(new RepositoryImportItem(url, "", "FAILED",
                    error instanceof IllegalArgumentException ? error.getMessage() : "导入失败，请刷新仓库列表后重试"))), 2)
                .collectList().map(RepositoryImportResult::new)));
    }
    private Mono<RepositoryImportItem> importOne(RepositoryAccount account, String token, String url, RepositoryImportInput input) {
        var address = RepositoryAddress.parse(account.getSpec().getProvider(), url);
        var spec = new Project.Spec(); spec.setProvider(account.getSpec().getProvider()); spec.setRepositoryUrl(address.web());
        AccountAddress.validateProject(account, spec);
        String key = AccountAddress.key(spec.getProvider(), address.web());
        return store.all(Project.class).filter(p -> spec.getProvider().equals(p.getSpec().getProvider())
            && key.equals(AccountAddress.key(p.getSpec().getProvider(), p.getSpec().getRepositoryUrl()))).next()
            .map(p -> new RepositoryImportItem(address.web(), p.getMetadata().getName(), "SKIPPED", "仓库已添加"))
            .switchIfEmpty(Mono.defer(() -> accounts.catalog(account).repository(account.getSpec(), token, address.path())
                .onErrorMap(AccountService::platformError).flatMap(remote -> {
                    if (!key.equals(AccountAddress.key(spec.getProvider(), remote.url())))
                        return Mono.error(new IllegalArgumentException("仓库已重命名或迁移，请刷新列表后重新选择"));
                    spec.setDisplayName(limit(remote.displayName(), 100)); spec.setDescription(limit(remote.description(), 1000));
                    spec.setAccountName(account.getMetadata().getName()); spec.setSyncMinutes(input.syncMinutes()); spec.setEnabled(input.enabled());
                    return projects.save(new ProjectService.Input(null, spec, ""))
                        .map(p -> new RepositoryImportItem(remote.url(), p.getMetadata().getName(), "IMPORTED", "仓库已添加"));
                })));
    }
    private String limit(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }
}
