package com.xhhao.com.releasehub;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.content.MarkdownRenderer;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.notification.*;
import com.xhhao.com.releasehub.provider.ReleaseProvider;
import com.xhhao.com.releasehub.security.*;
import com.xhhao.com.releasehub.subscription.SubscriptionService;
import com.xhhao.com.releasehub.support.MemoryStore;
import com.xhhao.com.releasehub.sync.SyncService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class WorkflowTest {
    private final MemoryStore memory = new MemoryStore();
    private final SecretStore secrets = new SecretStore(memory.store);
    private final ReleaseProvider provider = mock(ReleaseProvider.class);
    private final NotificationChannel channel = mock(NotificationChannel.class);
    private SyncService sync;
    private NotificationService notifications;

    WorkflowTest() {
        when(provider.name()).thenReturn("GITHUB"); when(channel.name()).thenReturn("EMAIL");
        when(channel.send(any(), any(), any(), any())).thenReturn(Mono.empty());
        var names = mock(com.xhhao.com.releasehub.halo.HaloNameService.class);
        when(names.detect(any(), anyString())).thenReturn(Mono.empty());
        sync = new SyncService(memory.store, new com.xhhao.com.releasehub.account.ProjectCredentials(memory.store, secrets), new MarkdownRenderer(), List.of(provider), names);
        notifications = new NotificationService(memory.store, List.of(channel));
        var p = new Project(); p.setMetadata(HubStore.metadata("project-demo")); p.setSpec(new Project.Spec());
        p.getSpec().setDisplayName("Demo"); p.getSpec().setProvider("GITHUB"); p.getSpec().setRepositoryUrl("https://github.com/org/repo"); memory.put(p);
    }
    private void remote(String... ids) {
        var releases = Arrays.stream(ids).map(id -> new ReleaseProvider.RemoteRelease(id, "v" + id, "Version " + id, "# Changes", Instant.parse("2026-01-01T00:00:00Z"), false,
            "https://github.com/org/repo/releases/tag/v" + id, List.<Release.Asset>of())).toList();
        when(provider.fetch(any(), anyString(), anyInt(), nullable(String.class))).thenReturn(Mono.just(new ReleaseProvider.Page(releases, true, false, null)));
    }
    private Subscription subscriber() {
        var s = new Subscription(); s.setMetadata(HubStore.metadata("email-demo")); s.setSpec(new Subscription.Spec());
        s.getSpec().setChannel("EMAIL"); s.getSpec().setState("ACTIVE"); s.getSpec().setEmail("demo@example.com");
        s.getSpec().setCreatedAt(Instant.now().minusSeconds(60)); s.getSpec().setVerifiedAt(Instant.now().minusSeconds(60)); s.getSpec().setTokenVersion(TokenService.random());
        return memory.put(s);
    }
    @Test void historicalImportIsSilentAndNewReleaseIsDeliveredExactlyOnceLocally() {
        subscriber(); remote("1"); assertEquals("SUCCEEDED", sync.sync("project-demo").block().getSpec().getState());
        notifications.fanout().block(); notifications.dispatch().block(); verify(channel, never()).send(any(), any(), any(), any());
        remote("2", "1"); sync.sync("project-demo").block(); sync.sync("project-demo").block();
        notifications.fanout().block(); notifications.fanout().block(); notifications.dispatch().block(); notifications.dispatch().block();
        assertEquals(2L, memory.store.all(Release.class).count().block());
        assertEquals(1L, memory.store.all(Delivery.class).count().block());
        assertEquals("SENT", memory.store.all(Delivery.class).blockFirst().getSpec().getState());
        verify(channel, times(1)).send(any(), any(), any(), any());
    }
    @Test void pendingNotificationIsCancelledAfterUnsubscribe() {
        var s = subscriber(); remote("1"); sync.sync("project-demo").block(); remote("2", "1"); sync.sync("project-demo").block(); notifications.fanout().block();
        s.getSpec().setState("UNSUBSCRIBED"); memory.client.update(s).block(); notifications.dispatch().block();
        verify(channel, never()).send(any(), any(), any(), any());
        assertEquals("CANCELLED", memory.store.all(Delivery.class).blockFirst().getSpec().getState());
    }
    @Test void failedDeliveryCanRecoverAfterRestartWithoutCreatingAnotherTask() {
        subscriber(); remote("1"); sync.sync("project-demo").block(); remote("2", "1"); sync.sync("project-demo").block(); notifications.fanout().block();
        when(channel.send(any(), any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("network")));
        notifications.dispatch().block();
        var d = memory.store.all(Delivery.class).blockFirst(); assertEquals("RETRY", d.getSpec().getState());
        d.getSpec().setState("SENDING"); d.getSpec().setLeaseUntil(Instant.now().minusSeconds(1)); d.getSpec().setNextAttempt(Instant.now().minusSeconds(1)); memory.client.update(d).block();
        when(channel.send(any(), any(), any(), any())).thenReturn(Mono.empty());
        new NotificationService(memory.store, List.of(channel)).dispatch().block();
        assertEquals(1L, memory.store.all(Delivery.class).count().block());
        var sent = memory.store.all(Delivery.class).blockFirst(); assertEquals("SENT", sent.getSpec().getState()); assertEquals(2, sent.getSpec().getAttempts());
    }
    @Test void failedMultiPageSyncDoesNotNotifyUntilACompleteSync() {
        subscriber(); remote("1"); sync.sync("project-demo").block();
        var newRelease = new ReleaseProvider.RemoteRelease("2", "v2", "Version 2", "Changes", Instant.now(), false, "https://github.com/org/repo", List.of());
        when(provider.fetch(any(), anyString(), eq(1), nullable(String.class))).thenReturn(Mono.just(new ReleaseProvider.Page(List.of(newRelease), false, false, null)));
        when(provider.fetch(any(), anyString(), eq(2), nullable(String.class))).thenReturn(Mono.error(new IllegalStateException("network")));
        assertEquals("FAILED", sync.sync("project-demo").block().getSpec().getState());
        notifications.fanout().block(); assertEquals(0L, memory.store.all(Delivery.class).count().block());
        remote("2", "1"); sync.sync("project-demo").block(); notifications.fanout().block();
        assertEquals(1L, memory.store.all(Delivery.class).count().block());
    }
    @Test void subscriptionRequiresVerificationAndOldUnsubscribeTokenCannotAffectResubscription() {
        var settings = mock(HubSettings.class); var mail = new HubSettings.Mail(); mail.setEnabled(true);
        var general = new HubSettings.General(); general.setSiteUrl("https://example.com");
        when(settings.mail()).thenReturn(Mono.just(mail)); when(settings.general()).thenReturn(Mono.just(general));
        var tokens = new TokenService(memory.store, secrets);
        var service = new SubscriptionService(memory.store, tokens, secrets, settings, new RequestThrottle());
        service.subscribe(new SubscriptionService.EmailInput("test@example.com", List.of("project-demo"), false)).block();
        var s = memory.store.all(Subscription.class).blockFirst(); assertEquals("PENDING", s.getSpec().getState());
        var verify = tokens.issue(s, "verify").block(); assertThrows(IllegalArgumentException.class, () -> service.action(verify, "unsubscribe").block());
        service.action(verify, "verify").block(); s = memory.get(Subscription.class, s.getMetadata().getName()); assertEquals("ACTIVE", s.getSpec().getState());
        String unsubscribe = tokens.issue(s, "unsubscribe").block(); service.action(unsubscribe, "unsubscribe").block();
        assertEquals("UNSUBSCRIBED", memory.get(Subscription.class, s.getMetadata().getName()).getSpec().getState());
        assertThrows(IllegalArgumentException.class, () -> service.action(unsubscribe, "unsubscribe").block());
    }
    @Test void subscriberJoiningAfterDiscoveryDoesNotReceiveEarlierRelease() {
        var s = subscriber(); var release = new Release(); release.setSpec(new Release.Spec()); release.getSpec().setProject("project-demo");
        release.getSpec().setDiscoveredAt(s.getSpec().getVerifiedAt().minusSeconds(1)); assertFalse(NotificationService.matches(s, release));
        release.getSpec().setDiscoveredAt(Instant.now()); release.getSpec().setPrerelease(true); assertFalse(NotificationService.matches(s, release));
        s.getSpec().setIncludePrerelease(true); assertTrue(NotificationService.matches(s, release));
        s.getSpec().setProjects(List.of("another-project")); assertFalse(NotificationService.matches(s, release));
    }
}
