package com.xhhao.com.releasehub.model;

import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "release-hub.xhhao.com", version = "v1alpha1", kind = "RepositoryAccount",
    plural = "repositoryaccounts", singular = "repositoryaccount")
public class RepositoryAccount extends AbstractExtension {
    private Spec spec;
    private Status status = new Status();

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "RepositoryAccountSpec")
    public static class Spec {
        private String displayName;
        private String provider;
        private String instanceUrl;
        private String credentialSecret;
    }

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "RepositoryAccountStatus")
    public static class Status {
        private String remoteId;
        private String username;
        private Instant verifiedAt;
    }
}
