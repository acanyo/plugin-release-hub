package com.xhhao.com.releasehub.notification;

import com.xhhao.com.releasehub.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class NotificationService {
    private final HubStore store;
    private final Map<String, NotificationChannel> channels = new HashMap<>();
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    public NotificationService(HubStore store, List<NotificationChannel> channels) {
        this.store = store; channels.forEach(c -> this.channels.put(c.name(), c));
    }
    public Mono<Void> fanout() {
        // Repair verification outbox after a crash between creating the subscription and its delivery.
        return store.all(Subscription.class).filter(s -> "PENDING".equals(s.getSpec().getState())
            && "EMAIL".equals(s.getSpec().getChannel()) && s.getSpec().getCreatedAt().isAfter(Instant.now().minusSeconds(86400)))
            .concatMap(s -> enqueue(s, null, "VERIFY", HubStore.id("verify", s.getMetadata().getName() + s.getSpec().getTokenVersion())))
            .thenMany(store.all(Release.class).filter(r -> r.getSpec().isNotificationPending()))
            .concatMap(r -> store.client.get(Project.class, r.getSpec().getProject())
                .filter(p -> p.getSpec().isEnabled() && p.getStatus().isInitialized()
                    && p.getStatus().getLastSuccessfulSync() != null
                    && !p.getStatus().getLastSuccessfulSync().isBefore(r.getSpec().getDiscoveredAt()))
                .flatMap(p -> store.all(Subscription.class).filter(s -> matches(s, r)).concatMap(s -> enqueue(s, r, "RELEASE",
                    HubStore.id("delivery", r.getMetadata().getName() + ":" + s.getMetadata().getName()))).then(Mono.defer(() -> {
                        r.getSpec().setNotificationPending(false); return store.client.update(r);
                    })))).then();
    }
    public static boolean matches(Subscription s, Release r) {
        return "ACTIVE".equals(s.getSpec().getState())
            && s.getSpec().getVerifiedAt() != null && !s.getSpec().getVerifiedAt().isAfter(r.getSpec().getDiscoveredAt())
            && (s.getSpec().getProjects().isEmpty() || s.getSpec().getProjects().contains(r.getSpec().getProject()))
            && (s.getSpec().isIncludePrerelease() || !r.getSpec().isPrerelease());
    }
    public Mono<Delivery> test(String subscription) {
        return store.client.get(Subscription.class, subscription).filter(s -> "ACTIVE".equals(s.getSpec().getState()))
            .switchIfEmpty(Mono.error(new IllegalArgumentException("仅可向已验证且启用的订阅发送测试通知")))
            .flatMap(s -> enqueue(s, null, "TEST", "test-" + UUID.randomUUID()));
    }
    private Mono<Delivery> enqueue(Subscription s, Release r, String type, String id) {
        var d = new Delivery(); d.setMetadata(HubStore.metadata(id)); d.setSpec(new Delivery.Spec());
        d.getSpec().setSubscription(s.getMetadata().getName()); d.getSpec().setType(type);
        d.getSpec().setRelease(r == null ? null : r.getMetadata().getName()); d.getSpec().setCreatedAt(Instant.now());
        return store.client.fetch(Delivery.class, id).switchIfEmpty(Mono.defer(() -> store.createOnce(d, Delivery.class)));
    }
    public Mono<Void> dispatch() {
        return store.all(Delivery.class).filter(d -> eligible(d, Instant.now())).take(100)
            .flatMap(d -> deliver(d.getMetadata().getName()).onErrorResume(e -> Mono.empty()), 4).then();
    }
    static boolean eligible(Delivery d, Instant now) {
        var s = d.getSpec();
        return ("QUEUED".equals(s.getState()) || "RETRY".equals(s.getState()) || "SENDING".equals(s.getState()))
            && (s.getNextAttempt() == null || !s.getNextAttempt().isAfter(now))
            && (s.getLeaseUntil() == null || !s.getLeaseUntil().isAfter(now));
    }
    private Mono<Void> deliver(String id) {
        return Mono.defer(() -> {
            if (!running.add(id)) return Mono.empty();
            return store.client.get(Delivery.class, id).filter(d -> eligible(d, Instant.now())).flatMap(d -> {
                if (d.getSpec().getAttempts() >= 5) return finish(d, new IllegalStateException("发送尝试已用尽"));
                d.getSpec().setLeaseUntil(Instant.now().plusSeconds(120)); d.getSpec().setState("SENDING");
                d.getSpec().setAttempts(d.getSpec().getAttempts() + 1);
                return store.client.update(d).flatMap(claimed -> send(claimed).timeout(Duration.ofSeconds(75))
                    .then(Mono.defer(() -> finish(claimed, null))).onErrorResume(e -> finish(claimed, e)));
            }).then().doFinally(signal -> running.remove(id));
        });
    }
    private Mono<Void> send(Delivery d) {
        return store.client.fetch(Subscription.class, d.getSpec().getSubscription())
            .switchIfEmpty(Mono.defer(() -> { d.getSpec().setState("CANCELLED"); return Mono.empty(); })).flatMap(sub -> {
            boolean verify = "VERIFY".equals(d.getSpec().getType());
            if ((!verify && !"ACTIVE".equals(sub.getSpec().getState())) || (verify && !"PENDING".equals(sub.getSpec().getState()))) {
                d.getSpec().setState("CANCELLED"); return Mono.empty();
            }
            if (verify && !d.getMetadata().getName().equals(HubStore.id("verify", sub.getMetadata().getName() + sub.getSpec().getTokenVersion()))) {
                d.getSpec().setState("CANCELLED"); return Mono.empty();
            }
            var channel = channels.get(sub.getSpec().getChannel());
            if (channel == null) return Mono.error(new IllegalArgumentException("通知渠道未注册"));
            if (d.getSpec().getRelease() == null) return channel.send(sub, d, null, null);
            return store.client.get(Release.class, d.getSpec().getRelease()).flatMap(r -> store.client.get(Project.class, r.getSpec().getProject())
                .flatMap(p -> {
                    if (!p.getSpec().isEnabled() || !matches(sub, r)) { d.getSpec().setState("CANCELLED"); return Mono.empty(); }
                    return channel.send(sub, d, r, p);
                }));
        }).then();
    }
    private Mono<Delivery> finish(Delivery d, Throwable error) {
        var s = d.getSpec(); s.setLeaseUntil(null);
        if (error == null) {
            if (!"CANCELLED".equals(s.getState())) { s.setState("SENT"); s.setSentAt(Instant.now()); }
            s.setMessage(null);
        } else {
            s.setState(s.getAttempts() >= 5 ? "FAILED" : "RETRY");
            s.setNextAttempt(Instant.now().plusSeconds(Math.min(3600, 30L << s.getAttempts())));
            s.setMessage(error instanceof NotificationChannel.Failure ? error.getMessage()
                : "发送失败（" + error.getClass().getSimpleName() + "），请检查渠道配置与连接");
        }
        return store.client.update(d);
    }
    public Mono<Delivery> retry(String id) {
        return store.client.get(Delivery.class, id).flatMap(d -> {
            if (!"FAILED".equals(d.getSpec().getState())) return Mono.error(new IllegalArgumentException("仅可重试失败的任务"));
            d.getSpec().setState("QUEUED"); d.getSpec().setAttempts(0); d.getSpec().setNextAttempt(null);
            return store.client.update(d);
        });
    }
}
