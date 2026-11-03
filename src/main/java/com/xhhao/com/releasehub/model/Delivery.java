package com.xhhao.com.releasehub.model;

import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "release-hub.xhhao.com", version = "v1alpha1", kind = "HubDelivery", plural = "hubdeliveries", singular = "hubdelivery")
public class Delivery extends AbstractExtension {
    private Spec spec;
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "HubDeliverySpec")
    public static class Spec {
        private String subscription;
        private String release;
        private String type = "RELEASE";
        private String state = "QUEUED";
        private String message;
        private int attempts;
        private Instant createdAt;
        private Instant nextAttempt;
        private Instant leaseUntil;
        private Instant sentAt;
    }
}
