package com.xhhao.com.releasehub.subscription;

import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SubscriptionService {
    private final HubStore store;
    private final TokenService tokens;
    private final SecretStore secrets;
    private final HubSettings settings;
    private final RequestThrottle throttle;
    public SubscriptionService(HubStore store, TokenService tokens, SecretStore secrets, HubSettings settings, RequestThrottle throttle) {
        this.store = store; this.tokens = tokens; this.secrets = secrets; this.settings = settings; this.throttle = throttle;
    }
    public record EmailInput(String email, List<String> projects, boolean includePrerelease) { }
    public record WebhookInput(String name, String url, List<String> projects, boolean includePrerelease, String secret, boolean enabled) { }

    public Mono<Void> subscribe(EmailInput input) {
        String email = input.email() == null ? "" : input.email().strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}"))
            return Mono.error(new IllegalArgumentException("请输入有效的邮箱地址"));
        throttle.check(HubStore.id("email", email), 3, 3600000);
        return settings.mail().flatMap(mail -> {
            if (!mail.isEnabled()) return Mono.error(new IllegalArgumentException("邮件订阅暂未开放"));
            return settings.general().doOnNext(HubSettings.General::baseUrl).then(validateProjects(input.projects()));
        }).flatMap(projects -> {
            String id = HubStore.id("email", email + ":" + String.join(",", projects) + ":" + input.includePrerelease());
            return store.client.fetch(Subscription.class, id).flatMap(existing -> {
                if ("ACTIVE".equals(existing.getSpec().getState())) return Mono.<Subscription>empty();
                if ("PENDING".equals(existing.getSpec().getState()) && existing.getSpec().getCreatedAt().isAfter(Instant.now().minusSeconds(600))) return Mono.<Subscription>empty();
                existing.getSpec().setState("PENDING"); existing.getSpec().setTokenVersion(TokenService.random());
                existing.getSpec().setCreatedAt(Instant.now());
                return store.client.update(existing).flatMap(this::verification);
            }).switchIfEmpty(Mono.defer(() -> store.client.fetch(Subscription.class, id).hasElement().flatMap(exists -> {
                if (exists) return Mono.empty();
                var s = fresh(id, "EMAIL", projects, input.includePrerelease()); s.getSpec().setEmail(email);
                return store.createOnce(s, Subscription.class).flatMap(this::verification);
            })));
        }).then();
    }
    private Mono<Subscription> verification(Subscription sub) {
        var d = new Delivery();
        d.setMetadata(HubStore.metadata(HubStore.id("verify", sub.getMetadata().getName() + sub.getSpec().getTokenVersion())));
        d.setSpec(new Delivery.Spec()); d.getSpec().setSubscription(sub.getMetadata().getName());
        d.getSpec().setType("VERIFY"); d.getSpec().setCreatedAt(Instant.now());
        return store.createOnce(d, Delivery.class).thenReturn(sub);
    }
    public Mono<Void> action(String token, String purpose) {
        return tokens.parse(token, purpose).flatMap(parts -> store.client.get(Subscription.class, parts[0]).flatMap(sub -> {
            if (!parts[3].equals(sub.getSpec().getTokenVersion()) || !"EMAIL".equals(sub.getSpec().getChannel()))
                return Mono.error(TokenService.invalid());
            if (purpose.equals("verify")) {
                if ("ACTIVE".equals(sub.getSpec().getState())) return Mono.just(sub);
                if (!"PENDING".equals(sub.getSpec().getState())) return Mono.error(TokenService.invalid());
                sub.getSpec().setState("ACTIVE"); sub.getSpec().setVerifiedAt(Instant.now());
            } else {
                sub.getSpec().setState("UNSUBSCRIBED"); sub.getSpec().setTokenVersion(TokenService.random());
            }
            return store.client.update(sub);
        })).then();
    }
    public Mono<Subscription> webhook(WebhookInput input) {
        SafeUrls.https(input.url());
        String id = input.name() == null || input.name().isBlank() ? "webhook-" + UUID.randomUUID() : input.name();
        return validateProjects(input.projects()).flatMap(projects -> store.client.fetch(Subscription.class, id)
            .defaultIfEmpty(fresh(id, "WEBHOOK", projects, input.includePrerelease())).flatMap(sub -> {
                if (!"WEBHOOK".equals(sub.getSpec().getChannel())) return Mono.error(new IllegalArgumentException("渠道不匹配"));
                boolean exists = sub.getMetadata().getVersion() != null;
                String secret = input.secret() == null ? "" : input.secret();
                if ((!exists || !secret.isBlank()) && secret.length() < 24) return Mono.error(new IllegalArgumentException("Webhook 签名密钥至少 24 个字符"));
                Mono<String> credential = secret.isBlank() ? Mono.just(sub.getSpec().getCredentialSecret())
                    : secrets.put("release-hub-" + id, "signingKey", secret);
                return credential.flatMap(name -> {
                    sub.getSpec().setCredentialSecret(name); sub.getSpec().setWebhookUrl(input.url());
                    sub.getSpec().setProjects(projects); sub.getSpec().setIncludePrerelease(input.includePrerelease());
                    sub.getSpec().setState(input.enabled() ? "ACTIVE" : "DISABLED");
                    if (!exists) sub.getSpec().setVerifiedAt(Instant.now());
                    return exists ? store.client.update(sub) : store.client.create(sub);
                });
            }));
    }
    private Mono<List<String>> validateProjects(List<String> projects) {
        if (projects == null || projects.isEmpty()) return Mono.just(List.of());
        if (projects.size() > 100) return Mono.error(new IllegalArgumentException("订阅项目过多"));
        var ids = projects.stream().distinct().sorted().toList();
        return reactor.core.publisher.Flux.fromIterable(ids).concatMap(id -> store.client.get(Project.class, id)
            .filter(p -> p.getSpec().isEnabled()).switchIfEmpty(Mono.error(new IllegalArgumentException("项目不存在或已停用"))))
            .then(Mono.just(ids));
    }
    private Subscription fresh(String id, String channel, List<String> projects, boolean pre) {
        var sub = new Subscription(); sub.setMetadata(HubStore.metadata(id)); sub.setSpec(new Subscription.Spec());
        var s = sub.getSpec(); s.setChannel(channel); s.setProjects(projects); s.setIncludePrerelease(pre);
        s.setCreatedAt(Instant.now()); s.setTokenVersion(TokenService.random()); return sub;
    }
}
