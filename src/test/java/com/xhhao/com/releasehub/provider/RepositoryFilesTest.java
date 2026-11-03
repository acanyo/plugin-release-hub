package com.xhhao.com.releasehub.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.model.Project;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

class RepositoryFilesTest {
    private final ProviderHttp http = mock(ProviderHttp.class);
    private final AtomicReference<URI> uri = new AtomicReference<>();
    private final AtomicReference<HttpHeaders> headers = new AtomicReference<>();
    private final ObjectNode response = new ObjectMapper().createObjectNode();
    private final RepositoryFiles files;
    RepositoryFilesTest() {
        var settings = mock(HubSettings.class); var config = new HubSettings.GitHub(); config.setApiBaseUrl("https://proxy.example/github");
        when(settings.github()).thenReturn(Mono.just(config));
        files = new RepositoryFiles(http, new GitHubApi(http, settings));
        response.put("type", "file").put("encoding", "base64").put("size", 12)
            .put("content", Base64.getMimeEncoder(20, new byte[]{'\n'}).encodeToString("名称：组件".getBytes(StandardCharsets.UTF_8)));
        when(http.get(any(), any())).thenAnswer(call -> {
            uri.set(call.getArgument(0)); var h = new HttpHeaders(); call.<Consumer<HttpHeaders>>getArgument(1).accept(h); headers.set(h);
            return Mono.just(new ProviderHttp.Response(response, false, null));
        });
    }
    private Project.Spec spec(String provider, String url) { var p = new Project.Spec(); p.setProvider(provider); p.setRepositoryUrl(url); return p; }
    @Test void githubUsesApiProxyAndExistingTokenWithMultilineBase64() {
        assertEquals("名称：组件", files.read(spec("GITHUB", "https://github.com/org/plugin-app"), "token", "src/main/resources/plugin.yaml").block());
        assertEquals("https://proxy.example/github/repos/org/plugin-app/contents/src/main/resources/plugin.yaml", uri.get().toString());
        assertEquals("Bearer token", headers.get().getFirst("Authorization"));
        assertNull(headers.get().getFirst("If-None-Match"));
    }
    @Test void giteeUsesDefaultBranchAndEncodedQueryCredential() {
        files.read(spec("GITEE", "https://gitee.com/org/app"), "token with spaces", "theme.yaml").block();
        assertEquals("https://gitee.com/api/v5/repos/org/app/contents/theme.yaml?access_token=token%20with%20spaces", uri.get().toString());
        assertNull(headers.get().getFirst("Authorization"));
    }
    @Test void gitlabUsesHeadAndEncodesNestedRepositoryAndFilePaths() {
        files.read(spec("GITLAB", "https://gitlab.com/group/team/app"), "token", "src/main/resources/plugin.yaml").block();
        assertEquals("https://gitlab.com/api/v4/projects/group%2Fteam%2Fapp/repository/files/src%2Fmain%2Fresources%2Fplugin.yaml?ref=HEAD", uri.get().toString());
        assertEquals("token", headers.get().getFirst("PRIVATE-TOKEN"));
    }
    @Test void cnbAcceptsBlobAndUsesDocumentedContentsApi() {
        response.put("type", "blob");
        assertEquals("名称：组件", files.read(spec("CNB", "https://cnb.cool/group/team/app"), "token", "theme.yaml").block());
        assertEquals("https://api.cnb.cool/group/team/app/-/git/contents/theme.yaml", uri.get().toString());
        assertEquals("application/vnd.cnb.api+json", headers.get().getFirst("Accept"));
        assertEquals("Bearer token", headers.get().getFirst("Authorization"));
    }
    @Test void missingFileIsEmptyWhileRateLimitsRemainErrors() {
        doReturn(Mono.error(new ProviderHttp.ProviderFailure(404, 0))).when(http).get(any(), any());
        assertNull(files.read(spec("GITHUB", "https://github.com/org/app"), "", "theme.yaml").block());
        doReturn(Mono.error(new ProviderHttp.ProviderFailure(429, 300))).when(http).get(any(), any());
        assertThrows(ProviderHttp.ProviderFailure.class, () -> files.read(spec("GITHUB", "https://github.com/org/app"), "", "theme.yaml").block());
    }
    @Test void rejectsSymlinksOversizeAndUnsupportedEncodingWithoutFollowingUrls() {
        response.put("type", "symlink").put("download_url", "https://attacker.example");
        assertNull(files.read(spec("GITHUB", "https://github.com/org/app"), "", "plugin.yaml").block());
        response.put("type", "file").put("size", 65537);
        assertNull(files.read(spec("GITHUB", "https://github.com/org/app"), "", "plugin.yaml").block());
        response.put("size", 10).put("encoding", "none");
        assertNull(files.read(spec("GITHUB", "https://github.com/org/app"), "", "plugin.yaml").block());
        verify(http, times(3)).get(any(), any());
    }
    @Test void rejectsTraversalBeforeAnyRequest() {
        for (String path : java.util.List.of("../plugin.yaml", "/plugin.yaml", "a/../theme.yaml", "theme.yaml?token=1"))
            assertThrows(IllegalArgumentException.class, () -> files.read(spec("GITHUB", "https://github.com/org/app"), "", path).block());
        verifyNoInteractions(http);
    }
    @Test void rejectsInvalidBase64AndUtf8() {
        response.put("content", "@@@");
        assertThrows(IllegalArgumentException.class, () -> files.read(spec("GITHUB", "https://github.com/org/app"), "", "plugin.yaml").block());
        response.put("content", "/w==");
        assertThrows(RuntimeException.class, () -> files.read(spec("GITHUB", "https://github.com/org/app"), "", "plugin.yaml").block());
    }
}
