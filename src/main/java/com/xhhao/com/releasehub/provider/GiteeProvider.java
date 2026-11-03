package com.xhhao.com.releasehub.provider;

import com.xhhao.com.releasehub.model.Project;
import java.net.URI;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GiteeProvider implements ReleaseProvider {
    private final ProviderHttp http;
    public GiteeProvider(ProviderHttp http) { this.http = http; }
    @Override public String name() { return "GITEE"; }
    @Override public Mono<Page> fetch(Project.Spec p, String token, int page, String etag) {
        var repo = RepositoryAddress.parse(name(), p.getRepositoryUrl());
        // Gitee's official v5 schema defines access_token as a query parameter. Never log this URI.
        String auth = token.isBlank() ? "" : "&access_token=" + RepositoryAddress.encode(token);
        return http.get(URI.create("https://gitee.com/api/v5/repos/" + repo.path() + "/releases?per_page=100&direction=desc&page=" + page + auth), h -> { })
            .map(r -> {
                if (!r.body().isArray()) throw new IllegalStateException("Gitee 返回结构不符合文档");
                var items = new ArrayList<RemoteRelease>();
                r.body().forEach(n -> { if (!n.path("draft").asBoolean()) items.add(ReleaseJson.standard(n,
                    repo.web() + "/releases/tag/" + RepositoryAddress.encode(ReleaseJson.text(n, "tag_name")), true)); });
                return new Page(items, r.body().size() < 100, false, null);
            });
    }
}
