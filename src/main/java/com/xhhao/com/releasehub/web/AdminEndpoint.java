package com.xhhao.com.releasehub.web;

import static com.xhhao.com.releasehub.web.ApiSupport.*;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.notification.NotificationService;
import com.xhhao.com.releasehub.service.ProjectService;
import com.xhhao.com.releasehub.subscription.SubscriptionService;
import java.time.Instant;
import java.util.*;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.*;

@Component
public class AdminEndpoint implements CustomEndpoint {
    private final HubStore store;
    private final ProjectService projects;
    private final SubscriptionService subscriptions;
    private final NotificationService notifications;
    public AdminEndpoint(HubStore store, ProjectService projects, SubscriptionService subscriptions, NotificationService notifications) {
        this.store = store; this.projects = projects; this.subscriptions = subscriptions; this.notifications = notifications;
    }
    public record Stats(long projects, long releases, long activeEmails, long activeWebhooks, long failedDeliveries) { }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .GET("stats", r -> Mono.zip(store.all(Project.class).count(), store.all(Release.class).count(),
                store.all(Subscription.class).filter(s -> "ACTIVE".equals(s.getSpec().getState()) && "EMAIL".equals(s.getSpec().getChannel()))
                    .map(s -> s.getSpec().getEmail()).distinct().count(),
                store.all(Subscription.class).filter(s -> "ACTIVE".equals(s.getSpec().getState()) && "WEBHOOK".equals(s.getSpec().getChannel())).count(),
                store.all(Delivery.class).filter(d -> "FAILED".equals(d.getSpec().getState())).count())
                .map(t -> new Stats(t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5())).flatMap(ApiSupport::ok),
                b -> operation(b, "getReleaseHubStats", "ReleaseHubAdmin", Stats.class, null))
            .GET("projects", r -> store.all(Project.class).sort(Comparator.comparingInt(p -> p.getSpec().getSortOrder())).collectList().flatMap(ApiSupport::ok),
                b -> operation(b, "listManagedProjects", "ReleaseHubAdmin", Project[].class, null))
            .POST("projects", r -> body(r, ProjectService.Input.class).flatMap(projects::save).flatMap(ApiSupport::ok),
                b -> operation(b, "saveReleaseProject", "ReleaseHubAdmin", Project.class, ProjectService.Input.class))
            .POST("projects/{name}/sync", r -> store.client.get(Project.class, r.pathVariable("name")).flatMap(p -> {
                if (!p.getSpec().isEnabled()) return Mono.error(new IllegalArgumentException("请先启用仓库"));
                if (p.getStatus().getLeaseUntil() != null && p.getStatus().getLeaseUntil().isAfter(Instant.now()))
                    return Mono.error(new IllegalArgumentException("该仓库正在同步"));
                p.getStatus().setNextSync(Instant.now()); return store.client.update(p);
            }).then(done("已加入同步队列")), b -> operation(b, "syncReleaseProject", "ReleaseHubAdmin", Result.class, null, "name"))
            .GET("subscriptions", r -> page(Subscription.class, r), b -> {
                operation(b, "listReleaseSubscriptions", "ReleaseHubAdmin", ListResult.generateGenericClass(Subscription.class), null); query(b, "page", Integer.class);
            })
            .POST("webhooks", r -> body(r, SubscriptionService.WebhookInput.class).flatMap(subscriptions::webhook).flatMap(ApiSupport::ok),
                b -> operation(b, "saveReleaseWebhook", "ReleaseHubAdmin", Subscription.class, SubscriptionService.WebhookInput.class))
            .PUT("subscriptions/{name}/state", r -> body(r, StateInput.class).flatMap(i -> store.client.get(Subscription.class,
                r.pathVariable("name")).flatMap(s -> {
                    if (!Set.of("ACTIVE", "DISABLED").contains(i.state())) return Mono.error(new IllegalArgumentException("状态无效"));
                    if ("ACTIVE".equals(i.state()) && s.getSpec().getVerifiedAt() == null) return Mono.error(new IllegalArgumentException("邮箱尚未验证"));
                    s.getSpec().setState(i.state()); return store.client.update(s);
                })).flatMap(ApiSupport::ok), b -> operation(b, "setReleaseSubscriptionState", "ReleaseHubAdmin", Subscription.class, StateInput.class, "name"))
            .DELETE("subscriptions/{name}", r -> store.client.get(Subscription.class, r.pathVariable("name")).flatMap(store.client::delete)
                .then(done("订阅已删除")), b -> operation(b, "deleteReleaseSubscription", "ReleaseHubAdmin", Result.class, null, "name"))
            .POST("subscriptions/{name}/test", r -> notifications.test(r.pathVariable("name")).flatMap(ApiSupport::ok),
                b -> operation(b, "testReleaseNotification", "ReleaseHubAdmin", Delivery.class, null, "name"))
            .GET("deliveries", r -> page(Delivery.class, r), b -> {
                operation(b, "listReleaseDeliveries", "ReleaseHubAdmin", ListResult.generateGenericClass(Delivery.class), null); query(b, "page", Integer.class);
            })
            .POST("deliveries/{name}/retry", r -> notifications.retry(r.pathVariable("name")).flatMap(ApiSupport::ok),
                b -> operation(b, "retryReleaseDelivery", "ReleaseHubAdmin", Delivery.class, null, "name"))
            .GET("sync-logs", r -> page(SyncLog.class, r), b -> {
                operation(b, "listReleaseSyncLogs", "ReleaseHubAdmin", ListResult.generateGenericClass(SyncLog.class), null); query(b, "page", Integer.class);
            })
            .onError(Throwable.class, ApiSupport::failure).build();
    }
    private <T extends Extension> Mono<ServerResponse> page(Class<T> type, ServerRequest r) {
        int page = Math.clamp(integer(r, "page", 1), 1, 100000);
        return store.all(type).sort(Comparator.comparing((T e) -> e.getMetadata().getCreationTimestamp()).reversed())
            .collectList().map(items -> new ListResult<>(page, 20, items.size(), items.stream().skip((long) (page - 1) * 20).limit(20).toList()))
            .flatMap(ApiSupport::ok);
    }
    @Override public GroupVersion groupVersion() { return new GroupVersion("console.api.release-hub.xhhao.com", "v1alpha1"); }
}
