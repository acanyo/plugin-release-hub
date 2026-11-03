package com.xhhao.com.releasehub.model;

import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "release-hub.xhhao.com", version = "v1alpha1", kind = "HubSyncLog", plural = "hubsynclogs", singular = "hubsynclog")
public class SyncLog extends AbstractExtension {
    private Spec spec;
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "HubSyncLogSpec")
    public static class Spec {
        private String project;
        private Instant startedAt;
        private Instant finishedAt;
        private String state;
        private String message;
        private int imported;
        private boolean initialImport;
    }
}
