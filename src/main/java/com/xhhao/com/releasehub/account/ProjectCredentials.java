package com.xhhao.com.releasehub.account;

import com.xhhao.com.releasehub.model.HubStore;
import com.xhhao.com.releasehub.model.Project;
import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.security.SecretStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProjectCredentials {
    private final HubStore store;
    private final SecretStore secrets;
    public ProjectCredentials(HubStore store, SecretStore secrets) { this.store = store; this.secrets = secrets; }

    public Mono<String> token(Project.Spec project) {
        if (project.getAccountName() == null || project.getAccountName().isBlank())
            return project.getCredentialSecret() == null || project.getCredentialSecret().isBlank() ? Mono.just("")
                : secrets.token(project.getCredentialSecret());
        return account(project).flatMap(a -> secrets.token(a.getSpec().getCredentialSecret()));
    }

    public Mono<RepositoryAccount> account(Project.Spec project) {
        return store.client.fetch(RepositoryAccount.class, project.getAccountName())
            .switchIfEmpty(Mono.error(new IllegalArgumentException("仓库账号不存在，请重新选择账号")))
            .doOnNext(a -> AccountAddress.validateProject(a, project));
    }
}
