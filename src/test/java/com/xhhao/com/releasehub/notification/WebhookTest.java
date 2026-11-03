package com.xhhao.com.releasehub.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

class WebhookTest {
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<HttpHeaders> headers = new AtomicReference<>();
    private WebhookChannel channel(HttpStatus responseStatus) {
        var outbound = mock(OutboundHttp.class);
        when(outbound.client()).thenReturn(WebClient.builder().exchangeFunction(request -> {
            headers.set(request.headers()); var output = new MockClientHttpRequest(request.method(), request.url());
            var context = new BodyInserter.Context() {
                @Override public List<HttpMessageWriter<?>> messageWriters() { return ExchangeStrategies.withDefaults().messageWriters(); }
                @Override public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() { return Optional.empty(); }
                @Override public Map<String, Object> hints() { return Map.of(); }
            };
            return request.body().insert(output, context).then(Mono.defer(output::getBodyAsString)).map(text -> {
                body.set(text); return ClientResponse.create(responseStatus).build();
            });
        }).build());
        var secrets = mock(SecretStore.class); when(secrets.read(anyString(), eq("signingKey"))).thenReturn(Mono.just("a-long-test-signing-key-42"));
        var settings = mock(HubSettings.class); var general = new HubSettings.General(); general.setSiteUrl("https://example.com"); general.setPageUrl("https://example.com/updates");
        when(settings.general()).thenReturn(Mono.just(general));
        return new WebhookChannel(outbound, secrets, settings, new ObjectMapper());
    }
    private Subscription sub() {
        var s = new Subscription(); s.setSpec(new Subscription.Spec()); s.getSpec().setWebhookUrl("https://hooks.example.com/releases"); s.getSpec().setCredentialSecret("webhook-test"); return s;
    }
    private Delivery delivery() {
        var d = new Delivery(); d.setMetadata(HubStore.metadata("test-delivery-42")); d.setSpec(new Delivery.Spec()); d.getSpec().setType("TEST"); return d;
    }
    @Test void signsExactBodyAndUsesStableIdempotencyKey() throws Exception {
        channel(HttpStatus.OK).send(sub(), delivery(), null, null).block();
        String timestamp = headers.get().getFirst("X-Release-Hub-Timestamp");
        assertEquals("sha256=" + TokenService.sign("a-long-test-signing-key-42", timestamp + "." + body.get()), headers.get().getFirst("X-Release-Hub-Signature"));
        assertEquals("test-delivery-42", headers.get().getFirst("Idempotency-Key"));
        assertEquals("release.test", new ObjectMapper().readTree(body.get()).get("event").asText());
    }
    @Test void rejectsRedirectResponsesInsteadOfFollowingThem() {
        assertThrows(IllegalStateException.class, () -> channel(HttpStatus.FOUND).send(sub(), delivery(), null, null).block());
    }
}
