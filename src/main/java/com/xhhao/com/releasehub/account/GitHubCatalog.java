package com.xhhao.com.releasehub.account;

import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.provider.GitHubApi;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GitHubCatalog implements RepositoryCatalog {
    private final GitHubApi api;
    public GitHubCatalog(GitHubApi api) { this.api = api; }
    @Override public String name() { return "GITHUB"; }
    @Override public Mono<Identity> identity(RepositoryAccount.Spec a, String token) {
        return api.get("/user", token, null).map(r -> CatalogJson.identity(r.body(), "login"));
    }
    @Override public Mono<RepositoryPage> repositories(RepositoryAccount.Spec a, String token, int page) {
        return api.get("/user/repos?visibility=all&affiliation=owner,collaborator,organization_member&sort=full_name&direction=asc&per_page="
            + PAGE_SIZE + "&page=" + page, token, null).map(r -> CatalogJson.page(r.body(), n -> decode(a, n)));
    }
    @Override public Mono<RemoteRepository> repository(RepositoryAccount.Spec a, String token, String path) {
        return api.get("/repos/" + path, token, null).map(r -> decode(a, r.body()));
    }
    private RemoteRepository decode(RepositoryAccount.Spec a, com.fasterxml.jackson.databind.JsonNode n) {
        return CatalogJson.repository(a, n, CatalogJson.text(n, "full_name"), CatalogJson.text(n, "name"),
            n.path("private").asBoolean(), n.path("archived").asBoolean());
    }
}
