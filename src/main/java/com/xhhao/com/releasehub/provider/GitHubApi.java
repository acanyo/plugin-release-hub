package com.xhhao.com.releasehub.provider;

import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.security.SafeUrls;
import java.net.URI;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** The configured reverse proxy replaces only GitHub's API origin, never public links. */
@Component
public class GitHubApi {
    private final ProviderHttp http;
    private final HubSettings settings;
    public GitHubApi(ProviderHttp http, HubSettings settings) { this.http = http; this.settings = settings; }

    public Mono<ProviderHttp.Response> get(String pathAndQuery, String token, String etag) {
        return settings.github().flatMap(config -> http.get(destination(config.getApiBaseUrl(), pathAndQuery), h -> {
            h.set("Accept", "application/vnd.github+json");
            h.set("X-GitHub-Api-Version", "2022-11-28");
            if (!token.isBlank()) h.setBearerAuth(token);
            if (etag != null && !etag.isBlank()) h.setIfNoneMatch(etag);
        }));
    }

    public static URI destination(String configuredBase, String pathAndQuery) {
        String base = configuredBase == null || configuredBase.isBlank() ? "https://api.github.com" : configuredBase.strip();
        var uri = SafeUrls.https(base);
        if (uri.getQuery() != null || base.length() > 2000 || uri.getRawPath().contains("%")
            || java.util.Arrays.stream(uri.getPath().split("/")).anyMatch(segment -> segment.equals(".") || segment.equals("..")))
            throw new IllegalArgumentException("GitHub API 代理地址不能包含参数、编码路径或路径跳转");
        if (!pathAndQuery.startsWith("/") || pathAndQuery.startsWith("//") || pathAndQuery.contains("#"))
            throw new IllegalArgumentException("GitHub API 路径不正确");
        return URI.create(base.replaceAll("/+$", "") + pathAndQuery);
    }
}
