package com.xhhao.com.releasehub.provider;

import com.xhhao.com.releasehub.model.Project;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GitHubProvider implements ReleaseProvider {
    private final GitHubApi api;
    public GitHubProvider(GitHubApi api) { this.api = api; }
    @Override public String name() { return "GITHUB"; }
    @Override public Mono<Page> fetch(Project.Spec p, String token, int page, String etag) {
        var repo = RepositoryAddress.parse(name(), p.getRepositoryUrl());
        return api.get("/repos/" + repo.path() + "/releases?per_page=30&page=" + page, token, page == 1 ? etag : null).map(r -> {
            if (r.notModified()) return new Page(java.util.List.of(), true, true, etag);
            if (!r.body().isArray()) throw new IllegalStateException("GitHub 返回结构不符合文档");
            var items = new ArrayList<RemoteRelease>();
            r.body().forEach(n -> { if (!n.path("draft").asBoolean()) items.add(ReleaseJson.standard(n,
                repo.web() + "/releases/tag/" + RepositoryAddress.encode(ReleaseJson.text(n, "tag_name")), false)); });
            return new Page(items, r.body().size() < 30, false, r.etag());
        });
    }
}
