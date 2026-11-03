package com.xhhao.com.releasehub.notification;

import com.xhhao.com.releasehub.model.*;
import reactor.core.publisher.Mono;

/** Add a Spring component implementing this interface to register a future delivery channel. */
public interface NotificationChannel {
    /** Only controlled, credential-free messages may use this exception. */
    class Failure extends IllegalStateException {
        public Failure(String message) { super(message); }
    }
    String name();
    Mono<Void> send(Subscription subscriber, Delivery delivery, Release release, Project project);
}
