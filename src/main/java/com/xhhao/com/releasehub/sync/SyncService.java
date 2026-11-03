package com.xhhao.com.releasehub.sync;

import com.xhhao.com.releasehub.content.MarkdownRenderer;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.provider.*;
import com.xhhao.com.releasehub.account.ProjectCredentials;
import com.xhhao.com.releasehub.halo.HaloNameService;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SyncService {
    private final HubStore store;
    private final ProjectCredentials credentials;
    private final MarkdownRenderer markdown;
    private final HaloNameService names;
    private final Map<String, ReleaseProvider> providers = new HashMap<>();
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    public SyncService(HubStore store, ProjectCredentials credentials, MarkdownRenderer markdown, List<ReleaseProvider> providers, HaloNameService names) {
        this.store = store; this.credentials = credentials; this.markdown = markdown; this.names = names;
        providers.forEach(p -> this.providers.put(p.name(), p));
    }
    public Mono<Void> runDue() {
        return store.all(SyncLog.class).filter(log -> "RUNNING".equals(log.getSpec().getState())
            && log.getSpec().getStartedAt().isBefore(Instant.now().minusSeconds(600))).take(50)
            .concatMap(log -> {
                log.getSpec().setState("FAILED"); log.getSpec().setFinishedAt(Instant.now());
                log.getSpec().setMessage("同步任务已中断，将按仓库同步策略重试"); return store.client.update(log);
            }).thenMany(store.all(Project.class)).filter(p -> p.getSpec().isEnabled()
            && (p.getStatus().getNextSync() == null || p.getStatus().getNextSync().isBefore(Instant.now())))
            .flatMap(p -> sync(p.getMetadata().getName()).onErrorResume(e -> Mono.empty()), 2).then();
    }
    public Mono<SyncLog> sync(String id) {
        return Mono.defer(() -> {
            if (!running.add(id)) return Mono.error(new IllegalArgumentException("该仓库正在同步"));
            return store.client.get(Project.class, id).flatMap(p -> {
                if (!p.getSpec().isEnabled()) return Mono.error(new IllegalArgumentException("请先启用仓库"));
                if (p.getStatus().getLeaseUntil() != null && p.getStatus().getLeaseUntil().isAfter(Instant.now()))
                    return Mono.error(new IllegalArgumentException("该仓库正在同步"));
                p.getStatus().setLeaseUntil(Instant.now().plusSeconds(600));
                return store.client.update(p).flatMap(this::perform);
            }).doFinally(signal -> running.remove(id));
        });
    }
    private Mono<SyncLog> perform(Project p) {
        var log = new SyncLog(); log.setMetadata(HubStore.metadata("sync-" + UUID.randomUUID()));
        var spec = new SyncLog.Spec(); log.setSpec(spec);
        spec.setProject(p.getMetadata().getName()); spec.setStartedAt(Instant.now());
        spec.setState("RUNNING"); spec.setInitialImport(!p.getStatus().isInitialized());
        var imported = new AtomicInteger();
        var detection = new AtomicReference<HaloNameService.Detection>();
        var provider = providers.get(p.getSpec().getProvider());
        Mono<String> load = provider == null ? Mono.error(new IllegalArgumentException("未知平台"))
            : credentials.token(p.getSpec());
        return store.client.create(log).flatMap(saved -> load.flatMap(token -> names.detect(p, token).doOnNext(detection::set)
                .then(Mono.defer(() -> fetchPages(p, provider, token, 1, imported))))
            .timeout(Duration.ofMinutes(8))
            .flatMap(etag -> finish(p, saved, imported.get(), etag.orElse(null), detection.get(), null))
            .onErrorResume(error -> finish(p, saved, imported.get(), null, detection.get(), error)));
    }
    private Mono<Optional<String>> fetchPages(Project p, ReleaseProvider provider, String token, int page, AtomicInteger imported) {
        if (page > 1000) return Mono.error(new IllegalStateException("超出单次同步 100000 个版本的限制，请检查仓库"));
        return provider.fetch(p.getSpec(), token, page, p.getStatus().getEtag()).flatMap(result ->
            reactor.core.publisher.Flux.fromIterable(result.releases()).concatMap(remote -> upsert(p, remote).doOnNext(created -> {
                if (created) imported.incrementAndGet();
            })).then(result.last() ? Mono.just(Optional.ofNullable(result.etag()))
                : fetchPages(p, provider, token, page + 1, imported).map(ignored -> Optional.ofNullable(result.etag()))));
    }
    private Mono<Boolean> upsert(Project p, ReleaseProvider.RemoteRelease remote) {
        String id = HubStore.id("release", p.getMetadata().getName() + ":" + remote.tag());
        return store.client.fetch(Release.class, id).flatMap(existing -> {
            var current = existing.getSpec();
            var previous = new ReleaseProvider.RemoteRelease(current.getRemoteId(), current.getTag(), current.getTitle(), current.getMarkdown(),
                current.getPublishedAt(), current.isPrerelease(), current.getSourceUrl(), current.getAssets());
            if (previous.equals(remote)) return Mono.just(false);
            apply(existing.getSpec(), remote);
            return store.client.update(existing).thenReturn(false);
        }).switchIfEmpty(Mono.defer(() -> {
            var r = new Release(); r.setMetadata(HubStore.metadata(id)); r.setSpec(new Release.Spec());
            r.getSpec().setProject(p.getMetadata().getName()); r.getSpec().setRemoteId(remote.id());
            r.getSpec().setDiscoveredAt(Instant.now());
            r.getSpec().setNotificationPending(p.getStatus().isInitialized());
            apply(r.getSpec(), remote);
            return store.client.create(r).thenReturn(true);
        }));
    }
    private void apply(Release.Spec s, ReleaseProvider.RemoteRelease remote) {
        s.setRemoteId(remote.id());
        s.setTag(remote.tag()); s.setTitle(remote.title()); s.setMarkdown(remote.markdown());
        s.setHtml(markdown.render(remote.markdown())); s.setSummary(markdown.summary(s.getHtml()));
        s.setSourceUrl(remote.sourceUrl()); s.setPublishedAt(remote.publishedAt());
        s.setPrerelease(remote.prerelease()); s.setAssets(remote.assets());
    }
    private Mono<SyncLog> finish(Project original, SyncLog log, int imported, String etag, HaloNameService.Detection detection, Throwable error) {
        log.getSpec().setImported(imported); log.getSpec().setFinishedAt(Instant.now());
        log.getSpec().setState(error == null ? "SUCCEEDED" : "FAILED");
        String message = error == null ? "同步完成" : safeError(error);
        log.getSpec().setMessage(message);
        return store.client.get(Project.class, original.getMetadata().getName()).flatMap(p -> {
            var s = p.getStatus(); s.setLeaseUntil(null); s.setMessage(message); s.setLastSync(Instant.now());
            if (detection != null) {
                s.setHaloNameMessage(detection.message());
                if (detection.checkedAt() != null) {
                    s.setHaloNameCheckedAt(detection.checkedAt());
                    var manifest = detection.manifest();
                    s.setHaloKind(manifest == null ? null : manifest.kind());
                    s.setHaloDisplayName(manifest == null ? null : manifest.displayName());
                    s.setHaloManifestPath(manifest == null ? null : manifest.path());
                }
            }
            s.setFailures(error == null ? 0 : s.getFailures() + 1);
            long backoff = error == null ? p.getSpec().getSyncMinutes() * 60L : Math.min(86400, 60L << Math.min(s.getFailures(), 10));
            if (error instanceof ProviderHttp.ProviderFailure f && (f.status == 429 || f.status == 403)) backoff = Math.max(backoff, f.retrySeconds);
            s.setNextSync(Instant.now().plusSeconds(backoff));
            if (error == null) { s.setInitialized(true); s.setEtag(etag); s.setLastSuccessfulSync(Instant.now()); }
            return store.client.update(p);
        }).then(store.client.update(log));
    }
    public static String safeError(Throwable error) {
        // Do not persist exception messages: HTTP client exceptions may contain Gitee tokens or callback URLs.
        if (error instanceof ProviderHttp.ProviderFailure f) return f.getMessage();
        return "同步失败（" + error.getClass().getSimpleName() + "），请检查凭据、网络及平台响应";
    }
}
