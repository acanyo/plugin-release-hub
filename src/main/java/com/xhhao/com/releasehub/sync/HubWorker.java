package com.xhhao.com.releasehub.sync;

import com.xhhao.com.releasehub.notification.NotificationService;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

@Component
public class HubWorker {
    private static final Logger log = LoggerFactory.getLogger(HubWorker.class);
    private final SyncService sync;
    private final NotificationService notifications;
    private final AtomicBoolean syncBusy = new AtomicBoolean();
    private final AtomicBoolean notificationBusy = new AtomicBoolean();
    private ScheduledExecutorService executor;
    private Disposable syncTask;
    private Disposable notificationTask;
    public HubWorker(SyncService sync, NotificationService notifications) { this.sync = sync; this.notifications = notifications; }
    public synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> { var t = new Thread(r, "release-hub-worker"); t.setDaemon(true); return t; });
        executor.scheduleWithFixedDelay(this::tick, 10, 10, TimeUnit.SECONDS);
    }
    private void tick() {
        if (syncBusy.compareAndSet(false, true)) {
            syncTask = reactor.core.publisher.Mono.defer(sync::runDue).timeout(Duration.ofMinutes(10))
                .doFinally(signal -> syncBusy.set(false))
                .subscribe(ignored -> { }, error -> log.warn("Release Hub sync worker failed: {}", error.getClass().getSimpleName()));
        }
        if (notificationBusy.compareAndSet(false, true)) {
            notificationTask = reactor.core.publisher.Mono.defer(() -> notifications.fanout().then(notifications.dispatch()))
                .timeout(Duration.ofMinutes(3)).doFinally(signal -> notificationBusy.set(false))
                .subscribe(ignored -> { }, error -> log.warn("Release Hub notification worker failed: {}", error.getClass().getSimpleName()));
        }
    }
    public synchronized void stop() {
        if (executor != null) { executor.shutdownNow(); executor = null; }
        if (syncTask != null) syncTask.dispose();
        if (notificationTask != null) notificationTask.dispose();
        syncBusy.set(false); notificationBusy.set(false);
    }
}
