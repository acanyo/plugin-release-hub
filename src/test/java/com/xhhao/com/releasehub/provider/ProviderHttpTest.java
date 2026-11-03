package com.xhhao.com.releasehub.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.security.OutboundHttp;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

class ProviderHttpTest {
    private ProviderHttp client(ClientResponse response) {
        var outbound = mock(OutboundHttp.class);
        when(outbound.client()).thenReturn(WebClient.builder().exchangeFunction(request -> Mono.just(response)).build());
        return new ProviderHttp(outbound, new ObjectMapper());
    }
    @Test void explicitlyDecodesJsonWithoutDependingOnHostJacksonVersion() {
        var response = ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
            .header("ETag", "\"test\"").body("[{\"tag_name\":\"v1\"}]").build();
        var result = client(response).get(URI.create("https://example.com"), h -> { }).block();
        assertEquals("v1", result.body().get(0).get("tag_name").asText()); assertEquals("\"test\"", result.etag());
    }
    @Test void conditionalRequestHandlesNotModifiedWithNoBody() {
        var response = ClientResponse.create(HttpStatus.NOT_MODIFIED).build();
        assertTrue(client(response).get(URI.create("https://example.com"), h -> { }).block().notModified());
    }
    @Test void rateLimitRetainsRetryAfterButDoesNotLeakResponseOrUri() {
        var response = ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "900")
            .body("sensitive-token").build();
        var error = assertThrows(ProviderHttp.ProviderFailure.class,
            () -> client(response).get(URI.create("https://example.com?access_token=sensitive-token"), h -> { }).block());
        assertEquals(900, error.retrySeconds); assertFalse(error.getMessage().contains("sensitive-token"));
    }
}
