package com.xhhao.com.releasehub.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.security.TokenService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class EmailTest {
    private final HubSettings settings = mock(HubSettings.class);
    private final TokenService tokens = mock(TokenService.class);
    private final HaloEmailSender sender = mock(HaloEmailSender.class);
    private final HubSettings.Mail mail = new HubSettings.Mail();
    private final EmailChannel channel = new EmailChannel(settings, tokens, sender);
    private final Subscription sub = new Subscription();
    private final Delivery delivery = new Delivery();
    EmailTest() {
        mail.setEnabled(true);
        var general = new HubSettings.General(); general.setBrandName("<script>unsafe</script>"); general.setSiteUrl("https://example.com");
        when(settings.general()).thenReturn(Mono.just(general)); when(settings.mail()).thenReturn(Mono.just(mail));
        when(tokens.issue(any(), eq("verify"))).thenReturn(Mono.just("verify-token"));
        when(tokens.issue(any(), eq("unsubscribe"))).thenReturn(Mono.just("unsubscribe-token"));
        when(sender.send(any())).thenReturn(Mono.empty());
        sub.setSpec(new Subscription.Spec()); sub.getSpec().setEmail("test@example.com");
        delivery.setMetadata(HubStore.metadata("delivery-42")); delivery.setSpec(new Delivery.Spec());
    }
    private HaloEmailSender.Content captured() {
        var capture = ArgumentCaptor.forClass(HaloEmailSender.Content.class); verify(sender).send(capture.capture()); return capture.getValue();
    }
    @Test void verificationUsesSeparatePurposeBoundTokensAndEscapesBrand() {
        delivery.getSpec().setType("VERIFY"); channel.send(sub, delivery, null, null).block();
        var content = captured();
        assertTrue(content.html().contains("/releases/verify?token=verify-token"));
        assertEquals("https://example.com/releases/unsubscribe?token=unsubscribe-token", content.unsubscribeUrl());
        assertFalse(content.html().contains("<script>"));
        assertFalse(content.plain().contains("/unsubscribe"));
        assertEquals("test@example.com", content.to());
    }
    @Test void releaseUsesDetectedHaloNameAndCustomTemplateWithUnsubscribeFooter() {
        delivery.getSpec().setType("RELEASE"); mail.setSubject("{project} 更新 {version}");
        var project = new Project(); project.setSpec(new Project.Spec()); project.getSpec().setDisplayName("plugin-name"); project.getStatus().setHaloDisplayName("文章组件");
        var release = new Release(); release.setMetadata(HubStore.metadata("release-1")); release.setSpec(new Release.Spec());
        release.getSpec().setTag("1.0.0"); release.getSpec().setSummary("<b>updated</b>"); release.getSpec().setPublishedAt(Instant.parse("2026-01-01T00:00:00Z"));
        channel.send(sub, delivery, release, project).block();
        var content = captured(); assertEquals("文章组件 更新 1.0.0", content.subject());
        assertTrue(content.html().contains("/releases/versions/release-1"));
        assertTrue(content.html().contains("&lt;b&gt;updated&lt;/b&gt;"));
        assertEquals("delivery-42", content.id()); assertTrue(content.unsubscribeUrl().contains("unsubscribe-token"));
    }
    @Test void testNotificationUsesHaloSenderWithoutSmtpConfiguration() {
        delivery.getSpec().setType("TEST"); channel.send(sub, delivery, null, null).block();
        assertTrue(captured().subject().startsWith("测试通知"));
    }
    @Test void pluginSwitchStopsSendingAndTokenIssuance() {
        mail.setEnabled(false); assertThrows(NotificationChannel.Failure.class, () -> channel.send(sub, delivery, null, null).block());
        verifyNoInteractions(tokens, sender);
    }
    @Test void propagatesNativeDeliveryFailureForQueueRetry() {
        delivery.getSpec().setType("TEST"); when(sender.send(any())).thenReturn(Mono.error(new NotificationChannel.Failure("Halo 邮件发送失败")));
        assertThrows(NotificationChannel.Failure.class, () -> channel.send(sub, delivery, null, null).block());
    }
    @Test void acceptsExistingSettingsWithRemovedSmtpFields() throws Exception {
        var legacy = new ObjectMapper().readValue("{\"enabled\":true,\"host\":\"old.example\",\"port\":587,\"passwordSecret\":\"old-secret\",\"security\":\"STARTTLS\",\"subject\":\"更新 {version}\"}", HubSettings.Mail.class);
        assertTrue(legacy.isEnabled()); assertEquals("更新 {version}", legacy.getSubject());
        String json = new ObjectMapper().writeValueAsString(legacy); assertFalse(json.contains("host")); assertFalse(json.contains("passwordSecret"));
    }
}
