package com.xhhao.com.releasehub.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.model.Project;
import com.xhhao.com.releasehub.account.GitHubCatalog;
import com.xhhao.com.releasehub.model.RepositoryAccount;
import java.net.URI;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

class GitHubApiTest {
    @Test void proxyPreservesBasePrefixAndQueryWithoutRewritingPublicLinks() {
        assertEquals("https://api.github.com/user", GitHubApi.destination("", "/user").toString());
        assertEquals("https://proxy.example/api/repos/org/app/releases?page=2", GitHubApi.destination("https://proxy.example/api/", "/repos/org/app/releases?page=2").toString());
        assertEquals("https://proxy.example/https://api.github.com/user/repos?page=1", GitHubApi.destination("https://proxy.example/https://api.github.com/", "/user/repos?page=1").toString());
    }
    @Test void unsafeProxyAndEndpointValuesAreRejected() {
        for (String base : java.util.List.of("http://example.com", "https://localhost", "https://u:p@example.com", "https://example.com?token=secret", "https://example.com/a/../b", "https://example.com/%2e%2e"))
            assertThrows(IllegalArgumentException.class, () -> GitHubApi.destination(base, "/user"));
        assertThrows(IllegalArgumentException.class, () -> GitHubApi.destination("", "//attacker.example"));
    }
    @Test void identityDiscoveryAndReleaseSyncAllUseProxyWithCorrectAuthentication() throws Exception {
        var settings = mock(HubSettings.class); var config = new HubSettings.GitHub(); config.setApiBaseUrl("https://proxy.example/github");
        when(settings.github()).thenReturn(Mono.just(config));
        var http = mock(ProviderHttp.class); var uris = new ArrayList<URI>(); var headers = new ArrayList<HttpHeaders>();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(http.get(any(), any())).thenAnswer(call -> {
            URI uri = call.getArgument(0); uris.add(uri); var h = new HttpHeaders(); call.<Consumer<HttpHeaders>>getArgument(1).accept(h); headers.add(h);
            return Mono.just(new ProviderHttp.Response(mapper.readTree(uri.getPath().endsWith("/user") ? "{\"id\":1,\"login\":\"demo\"}" : "[]"), false, null));
        });
        var api = new GitHubApi(http, settings); var catalog = new GitHubCatalog(api); var a = new RepositoryAccount.Spec();
        a.setProvider("GITHUB"); a.setInstanceUrl("https://github.com");
        catalog.identity(a, "secret-token").block(); catalog.repositories(a, "secret-token", 2).block();
        var p = new Project.Spec(); p.setRepositoryUrl("https://github.com/org/app");
        new GitHubProvider(api).fetch(p, "secret-token", 1, "\"etag\"").block();
        assertEquals(3, uris.size()); assertTrue(uris.stream().allMatch(u -> u.getHost().equals("proxy.example") && u.getPath().startsWith("/github/")));
        assertTrue(headers.stream().allMatch(h -> "Bearer secret-token".equals(h.getFirst("Authorization"))));
        assertEquals("\"etag\"", headers.get(2).getFirst("If-None-Match"));
        assertTrue(uris.get(1).getQuery().contains("page=2"));
    }
    @Test void failedSettingsDoNotFallBackToDirectConnection() {
        var settings = mock(HubSettings.class); var http = mock(ProviderHttp.class);
        when(settings.github()).thenReturn(Mono.error(new IllegalStateException("settings unavailable")));
        assertThrows(IllegalStateException.class, () -> new GitHubApi(http, settings).get("/user", "token", null).block());
        verifyNoInteractions(http);
    }
}
