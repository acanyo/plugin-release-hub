package com.xhhao.com.releasehub.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.model.Project;
import java.net.URI;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ProviderTest {
    @Test void gitlabPrereleaseInferenceSupportsSemverAndAvoidsWordPrefixes() {
        assertTrue(ReleaseJson.pre("v1.2.3-canary.4"));
        assertTrue(ReleaseJson.pre("release-rc1"));
        assertFalse(ReleaseJson.pre("v1.2.3"));
        assertFalse(ReleaseJson.pre("release-preparation"));
    }
    private ProviderHttp http(String json) throws Exception {
        var http = mock(ProviderHttp.class);
        when(http.get(any(), any())).thenReturn(Mono.just(new ProviderHttp.Response(new ObjectMapper().readTree(json), false, "etag")));
        return http;
    }
    private Project.Spec spec(String url) { var p = new Project.Spec(); p.setRepositoryUrl(url); return p; }
    @Test void githubSkipsDraftsAndMapsDownloads() throws Exception {
        var http = http("""
            [{"id":42,"tag_name":"v2.0.0-rc.1","name":"Preview","body":"## Test","published_at":"2026-01-01T12:00:00Z","prerelease":true,
              "assets":[{"name":"app.jar","browser_download_url":"https://github.com/org/repo/releases/download/v2/app.jar","size":1024}]},
             {"id":43,"draft":true}]
            """);
        var settings = mock(com.xhhao.com.releasehub.config.HubSettings.class);
        when(settings.github()).thenReturn(Mono.just(new com.xhhao.com.releasehub.config.HubSettings.GitHub()));
        var page = new GitHubProvider(new GitHubApi(http, settings)).fetch(spec("https://github.com/org/repo"), "", 1, null).block();
        assertEquals(1, page.releases().size()); assertTrue(page.releases().getFirst().prerelease());
        assertEquals(1024, page.releases().getFirst().assets().getFirst().size());
    }
    @Test void giteeUsesDocumentedCreatedDateAndStringAssets() throws Exception {
        var http = http("""
            [{"id":1,"tag_name":"v1.0.0","name":"Stable","body":"Changes","created_at":"2026-01-01T20:00:00+08:00","assets":["https://gitee.com/org/repo/attach_files/1/download"]}]
            """);
        var page = new GiteeProvider(http).fetch(spec("https://gitee.com/org/repo"), "token with spaces", 2, null).block();
        assertEquals("2026-01-01T12:00:00Z", page.releases().getFirst().publishedAt().toString());
        assertEquals(1, page.releases().getFirst().assets().size());
        verify(http).get(argThat((URI uri) -> uri.toString().contains("access_token=token%20with%20spaces") && uri.toString().contains("direction=desc")), any());
    }
    @Test void gitlabEncodesNestedProjectAndHidesFutureReleases() throws Exception {
        var http = http("""
            [{"tag_name":"v1.0.0-rc1","name":"Release","description":"Changes","released_at":"2026-01-01T12:00:00Z","assets":{"links":[{"name":"App","direct_asset_url":"https://gitlab.com/group/repo/app.jar"}]}},
             {"tag_name":"v3.0.0","released_at":"2099-01-01T12:00:00Z"}]
            """);
        var page = new GitLabProvider(http).fetch(spec("https://gitlab.com/group/team/repo"), "token", 1, null).block();
        assertEquals(1, page.releases().size()); assertTrue(page.releases().getFirst().prerelease());
        verify(http).get(argThat((URI uri) -> uri.getRawPath().contains("group%2Fteam%2Frepo")), any());
    }
    @Test void cnbUsesPageSizeAndCurrentDownloadField() throws Exception {
        var http = http("""
            [{"id":"123","tag_name":"v1","name":"Version","body":"Changes","published_at":"2026-01-01T12:00:00Z","assets":[{"name":"App","browser_download_url":"https://cnb.cool/group/repo/-/releases/download/v1/app.jar"}]}]
            """);
        var page = new CnbProvider(http).fetch(spec("https://cnb.cool/group/repo"), "token", 1, null).block();
        assertEquals("123", page.releases().getFirst().id()); assertEquals(1, page.releases().getFirst().assets().size());
        verify(http).get(argThat((URI uri) -> uri.toString().contains("/-/releases?page_size=100&page=1")), any());
    }
}
