package com.xhhao.com.releasehub.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "release-hub.xhhao.com", version = "v1alpha1", kind = "ReleaseProject", plural = "releaseprojects", singular = "releaseproject")
public class Project extends AbstractExtension {
    private Spec spec;
    private Status status = new Status();

    /** Resolve the public name without overwriting the administrator's fallback. */
    public String displayName() {
        if (spec.isAutoDetectName() && status != null && status.getHaloDisplayName() != null
            && !status.getHaloDisplayName().isBlank()) return status.getHaloDisplayName();
        return spec.getDisplayName();
    }

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "ReleaseProjectSpec")
    public static class Spec {
        private String displayName;
        private boolean autoDetectName = true;
        private String description = "";
        private String provider;
        private String repositoryUrl;
        private String credentialSecret = "";
        private String accountName = "";
        private boolean enabled = true;
        private int sortOrder;
        private int syncMinutes = 60;
    }

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "ReleaseProjectStatus")
    public static class Status {
        private boolean initialized;
        private Instant lastSync;
        private Instant lastSuccessfulSync;
        private Instant nextSync;
        private Instant leaseUntil;
        private int failures;
        private String etag;
        private String message;
        private String haloKind;
        private String haloDisplayName;
        private String haloManifestPath;
        private Instant haloNameCheckedAt;
        private String haloNameMessage;
    }
}
