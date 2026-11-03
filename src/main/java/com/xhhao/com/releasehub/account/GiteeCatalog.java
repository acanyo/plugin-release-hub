package com.xhhao.com.releasehub.account;

import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.provider.ProviderHttp;
import com.xhhao.com.releasehub.provider.RepositoryAddress;
import java.net.URI;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GiteeCatalog implements RepositoryCatalog {
    private final ProviderHttp http;
    public GiteeCatalog(ProviderHttp http) { this.http = http; }
    @Override public String name() { return "GITEE"; }
    private Mono<ProviderHttp.Response> get(String path, String token) {
        return http.get(URI.create("https://gitee.com/api/v5" + path
            + (path.contains("?") ? "&" : "?") + "access_token=" + RepositoryAddress.encode(token)), h -> { });
    }
    @Override public Mono<Identity> identity(RepositoryAccount.Spec a, String token) {
        return get("/user", token).map(r -> CatalogJson.identity(r.body(), "login"));
    }
    @Override public Mono<RepositoryPage> repositories(RepositoryAccount.Spec a, String token, int page) {
        return get("/user/repos?visibility=all&sort=full_name&direction=asc&per_page=" + PAGE_SIZE + "&page=" + page, token)
            .map(r -> CatalogJson.page(r.body(), n -> decode(a, n)));
    }
    @Override public Mono<RemoteRepository> repository(RepositoryAccount.Spec a, String token, String path) {
        return get("/repos/" + path, token).map(r -> decode(a, r.body()));
    }
    private RemoteRepository decode(RepositoryAccount.Spec a, com.fasterxml.jackson.databind.JsonNode n) {
        var address = RepositoryAddress.parse(name(), CatalogJson.text(n, "html_url"));
        return CatalogJson.repository(a, n, address.path(), CatalogJson.text(n, "name"),
            n.path("private").asBoolean(), n.path("archived").asBoolean());
    }
}
