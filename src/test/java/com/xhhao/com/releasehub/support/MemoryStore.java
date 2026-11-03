package com.xhhao.com.releasehub.support;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.config.JsonConfiguration;
import com.xhhao.com.releasehub.model.HubStore;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.*;
import reactor.core.publisher.*;
import run.halo.app.extension.*;

/** Small CAS store used to exercise workflows across separate service instances and restarts. */
@SuppressWarnings({"unchecked", "rawtypes"})
public class MemoryStore {
    public final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    public final HubStore store = new HubStore(client);
    private final Map<String, Extension> data = new LinkedHashMap<>();
    private final ObjectMapper mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build();
    public MemoryStore() {
        when(client.fetch(any(Class.class), anyString())).thenAnswer(a -> Mono.defer(() -> Mono.justOrEmpty(copy(data.get(key(a.getArgument(0), a.getArgument(1)))))));
        when(client.get(any(Class.class), anyString())).thenAnswer(a -> Mono.defer(() -> Mono.justOrEmpty(copy(data.get(key(a.getArgument(0), a.getArgument(1)))))
            .switchIfEmpty(Mono.error(new IllegalArgumentException("资源不存在")))));
        when(client.listAll(any(Class.class), any(), any())).thenAnswer(a -> Flux.defer(() -> Flux.fromIterable(new ArrayList<>(data.values())))
            .filter(e -> a.<Class<?>>getArgument(0).isInstance(e) && e.getMetadata().getDeletionTimestamp() == null).map(this::copy));
        when(client.create(any())).thenAnswer(a -> Mono.fromCallable(() -> {
            Extension value = copy(a.getArgument(0)); String key = key(value.getClass(), value.getMetadata().getName());
            if (data.containsKey(key)) throw new DuplicateKeyException("duplicate");
            value.getMetadata().setVersion(1L); value.getMetadata().setCreationTimestamp(Instant.now()); data.put(key, value); return copy(value);
        }));
        when(client.update(any())).thenAnswer(a -> Mono.fromCallable(() -> {
            Extension value = copy(a.getArgument(0)); String key = key(value.getClass(), value.getMetadata().getName());
            Extension previous = data.get(key);
            if (previous == null || !Objects.equals(previous.getMetadata().getVersion(), value.getMetadata().getVersion())) throw new OptimisticLockingFailureException("conflict");
            value.getMetadata().setVersion(value.getMetadata().getVersion() + 1); data.put(key, value); return copy(value);
        }));
        when(client.delete(any())).thenAnswer(a -> Mono.fromCallable(() -> {
            Extension value = a.getArgument(0); data.remove(key(value.getClass(), value.getMetadata().getName())); return value;
        }));
    }
    private String key(Class<?> type, String name) { return type.getName() + ":" + name; }
    private <T extends Extension> T copy(T e) { return e == null ? null : (T) mapper.convertValue(e, e.getClass()); }
    public <T extends Extension> T put(T e) { return client.create(e).block(); }
    public <T extends Extension> T get(Class<T> type, String name) { return client.get(type, name).block(); }
}
