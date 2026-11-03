package run.halo.app.notification;

import reactor.core.publisher.Mono;

/** Test double for the host-owned extension, which is intentionally outside the plugin SDK. */
public class EmailNotifier implements ReactiveNotifier {
    public NotificationContext context;
    public int calls;
    public Mono<Void> result = Mono.empty();
    @Override public Mono<Void> notify(NotificationContext context) { this.context = context; calls++; return result; }
}
