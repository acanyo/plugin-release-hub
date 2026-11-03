package com.xhhao.com.releasehub.provider;

import com.xhhao.com.releasehub.model.Project;
import com.xhhao.com.releasehub.security.SafeUrls;
import java.net.URI;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GitLabProvider implements ReleaseProvider {
    private final ProviderHttp http;
    public GitLabProvider(ProviderHttp http) { this.http = http; }
    @Override public String name() { return "GITLAB"; }
    @Override public Mono<Page> fetch(Project.Spec p, String token, int page, String etag) {
        var repo = RepositoryAddress.parse(name(), p.getRepositoryUrl());
        return http.get(URI.create("https://" + repo.host() + "/api/v4/projects/" + RepositoryAddress.encode(repo.path())
            + "/releases?per_page=100&page=" + page), h -> { if (!token.isBlank()) h.set("PRIVATE-TOKEN", token); })
            .map(r -> {
                if (!r.body().isArray()) throw new IllegalStateException("GitLab 返回结构不符合文档");
                var items = new ArrayList<RemoteRelease>();
                r.body().forEach(n -> {
                    var published = ReleaseJson.date(n, "released_at");
                    // Scheduled future GitLab releases must not be published or notified early.
                    if (published.isAfter(java.time.Instant.now())) return;
                    String tag = ReleaseJson.text(n, "tag_name");
                    if (tag.isBlank()) throw new IllegalArgumentException("GitLab 版本缺少标签");
                    String url = SafeUrls.browserLink(ReleaseJson.text(n.path("_links"), "self"));
                    var assets = new ArrayList<com.xhhao.com.releasehub.model.Release.Asset>();
                    for (var asset : n.path("assets").path("links")) {
                        String download = SafeUrls.browserLink(ReleaseJson.text(asset, "direct_asset_url"));
                        if (download.isBlank()) download = SafeUrls.browserLink(ReleaseJson.text(asset, "url"));
                        if (!download.isBlank()) assets.add(new com.xhhao.com.releasehub.model.Release.Asset(ReleaseJson.text(asset, "name"), download, 0));
                    }
                    for (var source : n.path("assets").path("sources")) {
                        String download = SafeUrls.browserLink(ReleaseJson.text(source, "url"));
                        if (!download.isBlank()) assets.add(new com.xhhao.com.releasehub.model.Release.Asset("源码（" + ReleaseJson.text(source, "format") + "）", download, 0));
                    }
                    items.add(new RemoteRelease(tag, tag, ReleaseJson.text(n, "name"), ReleaseJson.text(n, "description"),
                        published, ReleaseJson.pre(tag), url.isBlank() ? repo.web() + "/-/releases/" + RepositoryAddress.encode(tag) : url,
                        assets));
                });
                return new Page(items, r.body().size() < 100, false, null);
            });
    }
}
