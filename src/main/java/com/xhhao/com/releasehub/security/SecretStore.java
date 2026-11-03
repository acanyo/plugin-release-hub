package com.xhhao.com.releasehub.security;

import com.xhhao.com.releasehub.model.HubStore;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Secret;

@Component
public class SecretStore {
    private final HubStore store;
    public SecretStore(HubStore store) { this.store = store; }

    public Mono<String> read(String name, String key) {
        if (name == null || name.isBlank()) return Mono.just("");
        return store.client.get(Secret.class, name).map(secret -> {
            if (secret.getData() != null && secret.getData().containsKey(key))
                return new String(secret.getData().get(key), StandardCharsets.UTF_8);
            if (secret.getStringData() != null && secret.getStringData().containsKey(key))
                return secret.getStringData().get(key);
            throw new IllegalArgumentException("Secret 缺少所需字段: " + key);
        });
    }

    public Mono<String> token(String name) {
        if (name == null || name.isBlank()) return Mono.error(new IllegalArgumentException("请选择包含 token 字段的 Secret"));
        return read(name, "token").map(String::strip).flatMap(value -> {
            if (value.isBlank() || value.length() > 4096 || value.chars().anyMatch(Character::isISOControl))
                return Mono.error(new IllegalArgumentException("Secret 的 token 字段不能为空，且必须是有效的访问 Token"));
            return Mono.just(value);
        });
    }

    public Mono<String> put(String name, String key, String value) {
        return store.client.fetch(Secret.class, name).flatMap(secret -> {
            secret.setStringData(Map.of(key, value));
            return store.client.update(secret);
        }).switchIfEmpty(Mono.defer(() -> {
            var secret = new Secret();
            secret.setMetadata(HubStore.metadata(name));
            secret.setType(Secret.SECRET_TYPE_OPAQUE);
            secret.setStringData(Map.of(key, value));
            return store.client.create(secret);
        })).thenReturn(name);
    }
}
