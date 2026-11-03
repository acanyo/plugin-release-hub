package com.xhhao.com.releasehub.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.security.OutboundHttp;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProviderHttp {
    private final OutboundHttp http;
    private final ObjectMapper mapper;
    public ProviderHttp(OutboundHttp http, ObjectMapper mapper) { this.http = http; this.mapper = mapper; }
    public record Response(JsonNode body, boolean notModified, String etag) { }
    public Mono<Response> get(URI uri, Consumer<HttpHeaders> headers) {
        return http.client().get().uri(uri).headers(headers).exchangeToMono(response -> {
            var status = response.statusCode();
            String etag = response.headers().asHttpHeaders().getETag();
            if (status.value() == 304) return response.releaseBody().thenReturn(new Response(null, true, etag));
            if (status.is2xxSuccessful()) return response.bodyToMono(byte[].class)
                .switchIfEmpty(Mono.error(new IllegalStateException("平台返回空响应")))
                .map(body -> {
                    try { return new Response(mapper.readTree(body), false, etag); }
                    catch (java.io.IOException e) { throw new IllegalStateException("平台返回了无效的 JSON"); }
                });
            long retrySeconds = retryAfter(response.headers().asHttpHeaders());
            return response.releaseBody().then(Mono.error(new ProviderFailure(status.value(), retrySeconds)));
        }).timeout(Duration.ofSeconds(30));
    }
    static long retryAfter(HttpHeaders headers) {
        try {
            String retry = headers.getFirst("Retry-After");
            if (retry != null) {
                try { return Math.clamp(Long.parseLong(retry), 60, 86400); }
                catch (NumberFormatException e) { return Math.clamp(Duration.between(Instant.now(),
                    java.time.ZonedDateTime.parse(retry, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds(), 60, 86400); }
            }
            String reset = headers.getFirst("X-RateLimit-Reset");
            if (reset != null) return Math.clamp(Long.parseLong(reset) - Instant.now().getEpochSecond(), 60, 86400);
        } catch (RuntimeException ignored) { }
        return 300;
    }
    public static class ProviderFailure extends RuntimeException {
        public final int status;
        public final long retrySeconds;
        public ProviderFailure(int status, long retrySeconds) {
            super("平台 API 返回 HTTP " + status);
            this.status = status; this.retrySeconds = retrySeconds;
        }
    }
}
