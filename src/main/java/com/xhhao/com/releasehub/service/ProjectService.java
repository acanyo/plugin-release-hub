package com.xhhao.com.releasehub.service;

import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.provider.RepositoryAddress;
import com.xhhao.com.releasehub.security.SecretStore;
import com.xhhao.com.releasehub.security.SecretAccess;
import com.xhhao.com.releasehub.account.ProjectCredentials;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProjectService {
    private final HubStore store;
    private final SecretStore secrets;
    private final ProjectCredentials credentials;
    private final SecretAccess secretAccess;
    public ProjectService(HubStore store, SecretStore secrets, ProjectCredentials credentials, SecretAccess secretAccess) {
        this.store = store; this.secrets = secrets; this.credentials = credentials; this.secretAccess = secretAccess;
    }
    public record Input(String name, Project.Spec spec, String credentialSecret) { }

    public Mono<Project> save(Input input) {
        var spec = input.spec();
        if (spec == null || spec.getDisplayName() == null || spec.getDisplayName().isBlank()
            || spec.getDisplayName().length() > 100 || spec.getDescription() == null || spec.getDescription().length() > 1000
            || spec.getSyncMinutes() < 5 || spec.getSyncMinutes() > 10080)
            return Mono.error(new IllegalArgumentException("仓库名称必填，同步间隔需为 5–10080 分钟"));
        var address = RepositoryAddress.parse(spec.getProvider(), spec.getRepositoryUrl());
        spec.setRepositoryUrl(address.web());
        spec.setAccountName(spec.getAccountName() == null ? "" : spec.getAccountName().strip());
        if (!spec.getAccountName().isBlank() && input.credentialSecret() != null && !input.credentialSecret().isBlank())
            return Mono.error(new IllegalArgumentException("选择仓库账号后无需单独选择 Secret"));
        boolean creating = input.name() == null || input.name().isBlank();
        String id = creating ? HubStore.id("project", spec.getProvider() + ":" + address.web()) : input.name();
        Mono<Void> validate = spec.getAccountName().isBlank() ? Mono.empty() : credentials.account(spec).then();
        return validate.then(store.client.fetch(Project.class, id).defaultIfEmpty(fresh(id))).flatMap(p -> {
            boolean exists = p.getMetadata().getVersion() != null;
            if (creating && exists) return Mono.error(new IllegalArgumentException("该仓库已添加，请编辑已有仓库"));
            if (exists && (!p.getSpec().getProvider().equals(spec.getProvider())
                || !p.getSpec().getRepositoryUrl().equals(spec.getRepositoryUrl())))
                return Mono.error(new IllegalArgumentException("已有仓库的平台与地址不能更换，请添加新仓库"));
            String previous = exists ? p.getSpec().getCredentialSecret() : "";
            String reference = input.credentialSecret() == null || !spec.getAccountName().isBlank() ? previous : input.credentialSecret().strip();
            Mono<Void> secret = reference.isBlank() || reference.equals(previous) ? Mono.empty()
                : secretAccess.requireRead(reference).then(secrets.token(reference)).then();
            return secret.then(Mono.defer(() -> {
                if (exists && !p.getSpec().isAutoDetectName() && spec.isAutoDetectName())
                    p.getStatus().setHaloNameCheckedAt(null);
                spec.setCredentialSecret(reference); p.setSpec(spec);
                return exists ? store.client.update(p) : store.client.create(p);
            }));
        });
    }
    private Project fresh(String id) {
        var p = new Project(); p.setMetadata(HubStore.metadata(id)); return p;
    }
}
