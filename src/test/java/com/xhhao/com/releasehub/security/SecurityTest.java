package com.xhhao.com.releasehub.security;

import static org.junit.jupiter.api.Assertions.*;
import com.xhhao.com.releasehub.content.MarkdownRenderer;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.provider.RepositoryAddress;
import com.xhhao.com.releasehub.support.MemoryStore;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityTest {
    @Test void temporaryKeyReadFailureDoesNotPoisonLaterRetries() {
        var client = org.mockito.Mockito.mock(run.halo.app.extension.ReactiveExtensionClient.class);
        var secretStore = org.mockito.Mockito.mock(SecretStore.class);
        var secret = new run.halo.app.extension.Secret(); secret.setMetadata(HubStore.metadata("release-hub-signing-key"));
        org.mockito.Mockito.when(client.fetch(run.halo.app.extension.Secret.class, "release-hub-signing-key"))
            .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("temporary")), reactor.core.publisher.Mono.just(secret));
        org.mockito.Mockito.when(secretStore.read("release-hub-signing-key", "key")).thenReturn(reactor.core.publisher.Mono.just("persisted-key"));
        var tokens = new TokenService(new HubStore(client), secretStore);
        var sub = new Subscription(); sub.setMetadata(HubStore.metadata("email-retry")); sub.setSpec(new Subscription.Spec()); sub.getSpec().setTokenVersion("generation");
        assertThrows(IllegalStateException.class, () -> tokens.issue(sub, "verify").block());
        assertNotNull(tokens.issue(sub, "verify").block());
    }
    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1", "2001:db8::1", "2002:7f00:1::"})
    void deniesNonPublicAddresses(String address) throws Exception { assertFalse(SafeUrls.publicAddress(InetAddress.getByName(address))); }
    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "https://user:password@example.com", "https://example.com:8443", "https://localhost/", "https://example.local/", "javascript:alert(1)"})
    void rejectsUnsafeUrls(String url) { assertThrows(IllegalArgumentException.class, () -> SafeUrls.https(url)); }
    @Test void acceptsPublicAddresses() throws Exception {
        assertTrue(SafeUrls.publicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(SafeUrls.publicAddress(InetAddress.getByName("2606:4700:4700::1111")));
    }
    @Test void repositoryHostCannotSpoofProvider() {
        assertThrows(IllegalArgumentException.class, () -> RepositoryAddress.parse("GITHUB", "https://github.com.evil.test/org/repo"));
        assertThrows(IllegalArgumentException.class, () -> RepositoryAddress.parse("GITHUB", "https://github.com/org/../repo"));
        assertEquals("team/nested/project", RepositoryAddress.parse("GITLAB", "https://gitlab.com/team/nested/project.git").path());
    }
    @Test void markdownCannotExecuteOrLoadTrackingImages() {
        var renderer = new MarkdownRenderer();
        String html = renderer.render("# Update\n<script>alert(1)</script>\n[bad](javascript:alert(1))\n![tracker](https://evil.test/pixel)\n[good](https://example.com)");
        assertFalse(html.contains("<script")); assertFalse(html.contains("href=\"javascript:")); assertFalse(html.contains("<img"));
        assertTrue(html.contains("<h1>Update</h1>")); assertTrue(html.contains("nofollow noopener noreferrer"));
    }
    @Test void tokensArePurposeBoundTamperProofAndSurviveRestart() {
        var memory = new MemoryStore(); var secrets = new SecretStore(memory.store); var tokenService = new TokenService(memory.store, secrets);
        var sub = new Subscription(); sub.setMetadata(HubStore.metadata("email-demo")); sub.setSpec(new Subscription.Spec()); sub.getSpec().setTokenVersion(TokenService.random());
        String token = tokenService.issue(sub, "verify").block();
        assertEquals("email-demo", new TokenService(memory.store, secrets).parse(token, "verify").block()[0]);
        assertThrows(IllegalArgumentException.class, () -> tokenService.parse(token, "unsubscribe").block());
        assertThrows(IllegalArgumentException.class, () -> tokenService.parse(token + "0", "verify").block());
    }
    @Test void verificationExpiresAfter24Hours() {
        var memory = new MemoryStore(); var secrets = new SecretStore(memory.store); var tokens = new TokenService(memory.store, secrets);
        var sub = new Subscription(); sub.setMetadata(HubStore.metadata("email-expired")); sub.setSpec(new Subscription.Spec()); sub.getSpec().setTokenVersion("generation");
        tokens.issue(sub, "verify").block();
        String key = secrets.read("release-hub-signing-key", "key").block();
        String value = "email-expired:verify:" + java.time.Instant.now().minusSeconds(86410).getEpochSecond() + ":generation";
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> tokens.parse(payload + "." + TokenService.sign(key, payload), "verify").block());
    }
    @Test void connectionResolverRejectsLiteralLoopback() throws Exception {
        var executor = new io.netty.util.concurrent.DefaultEventExecutor(); var group = new PublicAddressResolver();
        try {
            var result = group.getResolver(executor).resolve(java.net.InetSocketAddress.createUnresolved("127.0.0.1", 443)).await();
            assertFalse(result.isSuccess()); assertInstanceOf(IllegalArgumentException.class, result.cause());
        } finally { group.close(); executor.shutdownGracefully().sync(); }
    }
}
