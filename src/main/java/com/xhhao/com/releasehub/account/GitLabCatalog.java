package com.xhhao.com.releasehub.account;

import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.provider.ProviderHttp;
import com.xhhao.com.releasehub.provider.RepositoryAddress;
import java.net.URI;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GitLabCatalog implements RepositoryCatalog {
    private final ProviderHttp http;
    public GitLabCatalog(ProviderHttp http) { this.http = http; }
    @Override public String name() { return "GITLAB"; }
    private Mono<ProviderHttp.Response> get(RepositoryAccount.Spec a, String path, String token) {
        return http.get(URI.create(a.getInstanceUrl() + "/api/v4" + path), h -> h.set("PRIVATE-TOKEN", token));
    }
    @Override public Mono<Identity> identity(RepositoryAccount.Spec a, String token) {
        return get(a, "/user", token).map(r -> CatalogJson.identity(r.body(), "username"));
    }
    @Override public Mono<RepositoryPage> repositories(RepositoryAccount.Spec a, String token, int page) {
        return get(a, "/projects?membership=true&simple=true&order_by=path&sort=asc&per_page=" + PAGE_SIZE + "&page=" + page, token)
            .map(r -> CatalogJson.page(r.body(), n -> decode(a, n)));
    }
    @Override public Mono<RemoteRepository> repository(RepositoryAccount.Spec a, String token, String path) {
        return get(a, "/projects/" + RepositoryAddress.encode(path), token).map(r -> decode(a, r.body()));
    }
    private RemoteRepository decode(RepositoryAccount.Spec a, com.fasterxml.jackson.databind.JsonNode n) {
        return CatalogJson.repository(a, n, CatalogJson.text(n, "path_with_namespace"), CatalogJson.text(n, "name"),
            !"public".equals(CatalogJson.text(n, "visibility")), n.path("archived").asBoolean());
    }
}
