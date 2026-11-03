package com.xhhao.com.releasehub.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.security.SecretStore;
import com.xhhao.com.releasehub.security.TokenService;
import com.xhhao.com.releasehub.support.MemoryStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.notification.NotifierDescriptor;
import run.halo.app.notification.NotificationContext;
import run.halo.app.notification.ReactiveNotifier;
import run.halo.app.notification.UserIdentity;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

class HaloEmailSenderTest {
    private final MemoryStore memory = new MemoryStore();
    private final SecretStore secrets = new SecretStore(memory.store);
    private final ExtensionGetter extensions = mock(ExtensionGetter.class);
    private final run.halo.app.notification.EmailNotifier notifier = new run.halo.app.notification.EmailNotifier();
    private final HaloEmailSender sender = new HaloEmailSender(memory.store, secrets, new ObjectMapper(), extensions);
    private final HaloEmailSender.Content content = new HaloEmailSender.Content("test@example.com", "更新\r\n通知", "Plain", "<p>HTML</p>", "delivery-42", "https://example.com/releases/unsubscribe?token=safe");

    HaloEmailSenderTest() {
        var descriptor = new NotifierDescriptor(); descriptor.setMetadata(HubStore.metadata("default-email-notifier"));
        descriptor.setSpec(new NotifierDescriptor.Spec()); descriptor.getSpec().setNotifierExtName("halo-email-notifier"); memory.put(descriptor);
        when(extensions.getEnabledExtensions(ReactiveNotifier.class)).thenReturn(Flux.just(notifier));
        config(true, "system@example.com");
    }
    private void config(boolean enabled, String username) {
        secrets.put("notifier-setting-secret", "default-email-notifier.json", "{\"sender\":{\"enable\":" + enabled
            + ",\"host\":\"smtp.example.com\",\"port\":465,\"username\":\"" + username + "\",\"password\":\"private-system-password\"}}").block();
    }
    @Test void invokesNativeNotifierWithSystemSettingsAndAnonymousRecipient() {
        sender.send(content).block(); assertEquals(1, notifier.calls);
        var context = notifier.context;
        assertEquals("system@example.com", context.getSenderConfig().path("username").asText());
        assertEquals("test@example.com", UserIdentity.of(context.getMessage().getRecipient()).getEmail().orElseThrow());
        assertEquals("Plain", context.getMessage().getPayload().getRawBody());
        assertEquals("<p>HTML</p>", context.getMessage().getPayload().getHtmlBody());
        assertEquals("更新 通知", context.getMessage().getPayload().getTitle());
        assertEquals(content.unsubscribeUrl(), context.getMessage().getPayload().getAttributes().get("unsubscribeUrl"));
        assertEquals("delivery-42", context.getMessage().getPayload().getAttributes().get("releaseHubEventId"));
    }
    @Test void systemSwitchOffIsAFailureInsteadOfSilentSuccess() {
        config(false, "system@example.com");
        var failure = assertThrows(NotificationChannel.Failure.class, () -> sender.send(content).block());
        assertTrue(failure.getMessage().contains("未启用")); assertEquals(0, notifier.calls);
    }
    @Test void missingSystemConfigDoesNotSend() {
        memory.client.delete(memory.get(run.halo.app.extension.Secret.class, "notifier-setting-secret")).block();
        assertThrows(NotificationChannel.Failure.class, () -> sender.send(content).block()); assertEquals(0, notifier.calls);
    }
    @Test void malformedEmptyAndMissingSenderConfigsDoNotSend() {
        for (String json : List.of("", "{}", "{\"sender\":null}", "{malformed")) {
            secrets.put("notifier-setting-secret", "default-email-notifier.json", json).block();
            assertThrows(NotificationChannel.Failure.class, () -> sender.send(content).block());
        }
        assertEquals(0, notifier.calls);
    }
    @Test void missingOrDisabledNativeExtensionDoesNotSend() {
        when(extensions.getEnabledExtensions(ReactiveNotifier.class)).thenReturn(Flux.empty());
        assertThrows(NotificationChannel.Failure.class, () -> sender.send(content).block()); assertEquals(0, notifier.calls);
    }
    @Test void reflectsSystemConfigurationChangesWithoutRestart() {
        sender.send(content).block(); config(true, "updated@example.com"); sender.send(content).block();
        assertEquals("updated@example.com", notifier.context.getSenderConfig().path("username").asText()); assertEquals(2, notifier.calls);
    }
    @Test void nativeFailuresArePropagatedAndSensitiveMessagesAreSanitized() {
        notifier.result = Mono.error(new IllegalStateException("SMTP auth failed private-system-password test@example.com"));
        var failure = assertThrows(NotificationChannel.Failure.class, () -> sender.send(content).block());
        assertFalse(failure.getMessage().contains("private-system-password")); assertFalse(failure.getMessage().contains("test@example.com"));
    }
    @Test void durableQueueWaitsForNativeResultAndRetriesWithoutAnotherDelivery() {
        var settings = mock(HubSettings.class); var general = new HubSettings.General(); general.setSiteUrl("https://example.com");
        var mail = new HubSettings.Mail(); mail.setEnabled(true);
        when(settings.general()).thenReturn(Mono.just(general)); when(settings.mail()).thenReturn(Mono.just(mail));
        var channel = new EmailChannel(settings, new TokenService(memory.store, secrets), sender);
        var notifications = new NotificationService(memory.store, List.of(channel));
        var sub = new Subscription(); sub.setMetadata(HubStore.metadata("sub-test")); sub.setSpec(new Subscription.Spec());
        sub.getSpec().setChannel("EMAIL"); sub.getSpec().setState("ACTIVE"); sub.getSpec().setEmail("test@example.com");
        sub.getSpec().setVerifiedAt(Instant.now()); sub.getSpec().setTokenVersion(TokenService.random()); memory.put(sub);
        var delivery = notifications.test("sub-test").block();
        notifier.result = Mono.error(new IllegalStateException("native failure")); notifications.dispatch().block();
        delivery = memory.get(Delivery.class, delivery.getMetadata().getName()); assertEquals("RETRY", delivery.getSpec().getState());
        delivery.getSpec().setNextAttempt(null); memory.client.update(delivery).block();
        notifier.result = Mono.empty(); notifications.dispatch().block();
        assertEquals("SENT", memory.get(Delivery.class, delivery.getMetadata().getName()).getSpec().getState());
        assertEquals(1L, memory.store.all(Delivery.class).count().block());
        assertEquals(delivery.getMetadata().getName(), notifier.context.getMessage().getPayload().getAttributes().get("releaseHubEventId"));
    }
}
