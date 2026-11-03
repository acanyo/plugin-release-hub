package com.xhhao.com.releasehub.model;

import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "release-hub.xhhao.com", version = "v1alpha1", kind = "HubRelease", plural = "hubreleases", singular = "hubrelease")
public class Release extends AbstractExtension {
    private Spec spec;
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "HubReleaseSpec")
    public static class Spec {
        private String project;
        private String remoteId;
        private String tag;
        private String title;
        private String markdown;
        private String html;
        private String summary;
        private String sourceUrl;
        private Instant publishedAt;
        private Instant discoveredAt;
        private boolean prerelease;
        private boolean notificationPending;
        private List<Asset> assets = List.of();
    }
    public record Asset(String name, String url, long size) { }
}
