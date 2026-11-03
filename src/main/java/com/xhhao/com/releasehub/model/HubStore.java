package com.xhhao.com.releasehub.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.*;
import run.halo.app.extension.index.query.Queries;

@Component
public class HubStore {
    public final ReactiveExtensionClient client;
    public HubStore(ReactiveExtensionClient client) { this.client = client; }

    public <T extends Extension> Flux<T> all(Class<T> type) {
        return client.listAll(type, ListOptions.builder().fieldQuery(
            Queries.isNull("metadata.deletionTimestamp")).build(), Sort.unsorted());
    }

    public <T extends Extension> Mono<T> createOnce(T value, Class<T> type) {
        return client.create(value).onErrorResume(HubStore::duplicate,
            error -> client.get(type, value.getMetadata().getName()));
    }

    private static boolean duplicate(Throwable error) {
        // Halo wraps the storage DuplicateKeyException in its application-level DuplicateNameException.
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.springframework.dao.DuplicateKeyException) return true;
        }
        return false;
    }

    public static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }

    public static String id(String prefix, String value) {
        try {
            return prefix + "-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 40);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
