package com.xhhao.com.releasehub.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.security.*;
import com.xhhao.com.releasehub.web.PublishedPageLinks;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class WebhookChannel implements NotificationChannel {
    private final OutboundHttp http;
    private final SecretStore secrets;
    private final HubSettings settings;
    private final ObjectMapper mapper;
    public WebhookChannel(OutboundHttp http, SecretStore secrets, HubSettings settings, ObjectMapper mapper) {
        this.http = http; this.secrets = secrets; this.settings = settings; this.mapper = mapper;
    }
    @Override public String name() { return "WEBHOOK"; }
    @Override public Mono<Void> send(Subscription sub, Delivery delivery, Release release, Project project) {
        var target = SafeUrls.https(sub.getSpec().getWebhookUrl());
        return Mono.zip(secrets.read(sub.getSpec().getCredentialSecret(), "signingKey"), settings.general()).flatMap(tuple -> {
            if (tuple.getT1().isBlank()) return Mono.error(new IllegalArgumentException("Webhook 签名密钥未配置"));
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            Map<String, Object> event = Map.of("event", release == null ? "release.test" : "release.published",
                "id", delivery.getMetadata().getName(), "timestamp", timestamp,
                "project", project == null ? Map.of() : Map.of("id", project.getMetadata().getName(), "name", project.displayName()),
                "release", release == null ? Map.of() : Map.of("tag", release.getSpec().getTag(), "title", release.getSpec().getTitle(),
                    "publishedAt", release.getSpec().getPublishedAt().toString(), "prerelease", release.getSpec().isPrerelease(),
                    "summary", release.getSpec().getSummary(), "url", PublishedPageLinks.release(tuple.getT2(), release.getMetadata().getName())));
            String body;
            try { body = mapper.writeValueAsString(event); } catch (Exception e) { return Mono.error(e); }
            return http.client().post().uri(target).contentType(MediaType.APPLICATION_JSON)
                .header("X-Release-Hub-Id", delivery.getMetadata().getName())
                .header("Idempotency-Key", delivery.getMetadata().getName())
                .header("X-Release-Hub-Timestamp", timestamp)
                .header("X-Release-Hub-Signature", "sha256=" + TokenService.sign(tuple.getT1(), timestamp + "." + body))
                .bodyValue(body).exchangeToMono(r -> r.releaseBody().then(r.statusCode().is2xxSuccessful()
                    ? Mono.<Void>empty() : Mono.error(new NotificationChannel.Failure("Webhook HTTP " + r.statusCode().value()))))
                .timeout(Duration.ofSeconds(30));
        });
    }
}
