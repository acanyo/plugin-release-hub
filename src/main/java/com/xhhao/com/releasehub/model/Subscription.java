package com.xhhao.com.releasehub.model;

import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "release-hub.xhhao.com", version = "v1alpha1", kind = "HubSubscription", plural = "hubsubscriptions", singular = "hubsubscription")
public class Subscription extends AbstractExtension {
    private Spec spec;
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "HubSubscriptionSpec")
    public static class Spec {
        private String channel;
        private String email;
        private String webhookUrl;
        private String credentialSecret;
        private List<String> projects = List.of();
        private boolean includePrerelease;
        private String state = "PENDING";
        private Instant createdAt;
        private Instant verifiedAt;
        private String tokenVersion;
    }
}
