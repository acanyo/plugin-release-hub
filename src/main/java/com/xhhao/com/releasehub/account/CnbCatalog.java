package com.xhhao.com.releasehub.account;

import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.provider.ProviderHttp;
import java.net.URI;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CnbCatalog implements RepositoryCatalog {
    private final ProviderHttp http;
    public CnbCatalog(ProviderHttp http) { this.http = http; }
    @Override public String name() { return "CNB"; }
    private Mono<ProviderHttp.Response> get(String path, String token) {
        return http.get(URI.create("https://api.cnb.cool" + path), h -> {
            h.set("Accept", "application/vnd.cnb.api+json"); h.setBearerAuth(token);
        });
    }
    @Override public Mono<Identity> identity(RepositoryAccount.Spec a, String token) {
        return get("/user", token).map(r -> CatalogJson.identity(r.body(), "username"));
    }
    @Override public Mono<RepositoryPage> repositories(RepositoryAccount.Spec a, String token, int page) {
        return get("/user/repos?role=Guest&order_by=slug_path&desc=false&page_size=" + PAGE_SIZE + "&page=" + page, token)
            .map(r -> CatalogJson.page(r.body(), n -> decode(a, n)));
    }
    @Override public Mono<RemoteRepository> repository(RepositoryAccount.Spec a, String token, String path) {
        return get("/" + path, token).map(r -> decode(a, r.body()));
    }
    private RemoteRepository decode(RepositoryAccount.Spec a, com.fasterxml.jackson.databind.JsonNode n) {
        return CatalogJson.repository(a, n, CatalogJson.text(n, "path"), CatalogJson.text(n, "name"),
            !"Public".equals(CatalogJson.text(n, "visibility_level")), n.path("status").asInt() == 1);
    }
}
