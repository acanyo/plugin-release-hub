package com.xhhao.com.releasehub.provider;

import com.xhhao.com.releasehub.model.Project;
import java.net.URI;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CnbProvider implements ReleaseProvider {
    private final ProviderHttp http;
    public CnbProvider(ProviderHttp http) { this.http = http; }
    @Override public String name() { return "CNB"; }
    @Override public Mono<Page> fetch(Project.Spec p, String token, int page, String etag) {
        var repo = RepositoryAddress.parse(name(), p.getRepositoryUrl());
        return http.get(URI.create("https://api.cnb.cool/" + repo.path() + "/-/releases?page_size=100&page=" + page), h -> {
            h.set("Accept", "application/vnd.cnb.api+json"); if (!token.isBlank()) h.setBearerAuth(token);
        }).map(r -> {
            if (!r.body().isArray()) throw new IllegalStateException("CNB 返回结构不符合文档");
            var items = new ArrayList<RemoteRelease>();
            r.body().forEach(n -> { if (!n.path("draft").asBoolean()) items.add(ReleaseJson.standard(n,
                repo.web() + "/-/releases/tag/" + RepositoryAddress.encode(ReleaseJson.text(n, "tag_name")), false)); });
            return new Page(items, r.body().size() < 100, false, null);
        });
    }
}
