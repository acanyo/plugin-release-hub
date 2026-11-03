package com.xhhao.com.releasehub.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhhao.com.releasehub.model.HubStore;
import com.xhhao.com.releasehub.security.SecretStore;
import java.time.Instant;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.notification.NotifierDescriptor;
import run.halo.app.notification.NotificationContext;
import run.halo.app.notification.ReactiveNotifier;
import run.halo.app.notification.ReasonAttributes;
import run.halo.app.notification.UserIdentity;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

/** Use Halo's native email notifier and system-owned configuration; retain our durable queue. */
@Component
public class HaloEmailSender {
    private static final String DESCRIPTOR = "default-email-notifier";
    private final HubStore store;
    private final SecretStore secrets;
    private final ObjectMapper mapper;
    private final ExtensionGetter extensions;
    public HaloEmailSender(HubStore store, SecretStore secrets, ObjectMapper mapper, ExtensionGetter extensions) {
        this.store = store; this.secrets = secrets; this.mapper = mapper; this.extensions = extensions;
    }
    public record Content(String to, String subject, String plain, String html, String id, String unsubscribeUrl) { }

    public Mono<Void> send(Content content) {
        return Mono.zip(senderConfig(), notifier()).flatMap(tuple -> {
            var attributes = new ReasonAttributes();
            attributes.put("unsubscribeUrl", content.unsubscribeUrl());
            attributes.put("releaseHubEventId", content.id());
            var payload = new NotificationContext.MessagePayload();
            payload.setTitle(content.subject().replace("\r", "").replace("\n", " "));
            payload.setRawBody(content.plain()); payload.setHtmlBody(content.html()); payload.setAttributes(attributes);
            var message = new NotificationContext.Message();
            message.setRecipient(UserIdentity.anonymousWithEmail(content.to()).name());
            message.setPayload(payload); message.setTimestamp(Instant.now());
            var context = new NotificationContext();
            context.setMessage(message); context.setSenderConfig(tuple.getT1()); context.setReceiverConfig(mapper.createObjectNode());
            // Wait for SMTP completion instead of considering native queue acceptance a delivery success.
            return tuple.getT2().notify(context);
        }).onErrorMap(error -> error instanceof NotificationChannel.Failure ? error
            : new NotificationChannel.Failure("Halo 邮件发送失败，请检查系统通知设置、发件账号与服务连接"));
    }

    private Mono<ObjectNode> senderConfig() {
        // This is Halo 2.27's DefaultNotifierConfigStore layout. Never expose it through plugin APIs.
        return secrets.read("notifier-setting-secret", DESCRIPTOR + ".json")
            .flatMap(value -> Mono.fromCallable(() -> mapper.readTree(value)))
            .flatMap(root -> {
                var sender = root.path("sender");
                if (!(sender instanceof ObjectNode config) || !config.path("enable").asBoolean())
                    return Mono.error(new NotificationChannel.Failure("Halo 邮件通知器未启用，请先在系统通知设置中配置并启用"));
                return Mono.just(config);
            }).switchIfEmpty(Mono.error(new NotificationChannel.Failure("Halo 邮件通知尚未配置，请先在系统通知设置中配置并启用")));
    }

    private Mono<ReactiveNotifier> notifier() {
        return store.client.fetch(NotifierDescriptor.class, DESCRIPTOR)
            .filter(descriptor -> "halo-email-notifier".equals(descriptor.getSpec().getNotifierExtName()))
            .flatMap(descriptor -> extensions.getEnabledExtensions(ReactiveNotifier.class)
                .filter(notifier -> org.springframework.util.ClassUtils.getUserClass(notifier).getName()
                    .equals("run.halo.app.notification.EmailNotifier")).next())
            .switchIfEmpty(Mono.error(new NotificationChannel.Failure("Halo 内置邮件通知器不可用，请检查系统通知设置")));
    }
}
