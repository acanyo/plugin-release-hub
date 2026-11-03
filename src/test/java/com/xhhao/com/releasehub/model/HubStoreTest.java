package com.xhhao.com.releasehub.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;

class HubStoreTest {
    @Test void handlesHaloWrappedDuplicateWithoutCreatingAnotherDelivery() {
        var client = mock(ReactiveExtensionClient.class); var d = new Delivery(); d.setMetadata(HubStore.metadata("delivery-same"));
        when(client.create(d)).thenReturn(Mono.error(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Duplicate name", new org.springframework.dao.DuplicateKeyException("duplicate"))));
        when(client.get(Delivery.class, "delivery-same")).thenReturn(Mono.just(d));
        assertSame(d, new HubStore(client).createOnce(d, Delivery.class).block());
    }
}
