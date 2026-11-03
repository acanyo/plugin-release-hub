package com.xhhao.com.releasehub.security;

import io.netty.resolver.*;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Resolves on boundedElastic and validates the exact addresses handed to the connection. */
public final class PublicAddressResolver extends AddressResolverGroup<InetSocketAddress> {
    @Override protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
        return new AbstractAddressResolver<InetSocketAddress>(executor, InetSocketAddress.class) {
            @Override protected boolean doIsResolved(InetSocketAddress address) { return false; }
            @Override protected void doResolve(InetSocketAddress address, Promise<InetSocketAddress> promise) {
                var resolution = resolveAll(address);
                resolution.addListener(result -> {
                    if (result.isSuccess()) promise.trySuccess(resolution.getNow().getFirst());
                    else promise.tryFailure(result.cause());
                });
            }
            @Override protected void doResolveAll(InetSocketAddress address, Promise<List<InetSocketAddress>> promise) {
                Mono.fromCallable(() -> InetAddress.getAllByName(address.getHostString()))
                    .subscribeOn(Schedulers.boundedElastic()).timeout(Duration.ofSeconds(10))
                    .map(addresses -> {
                        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(a -> !SafeUrls.publicAddress(a)))
                            throw new IllegalArgumentException("出站目标不是公网地址");
                        return Arrays.stream(addresses).map(a -> new InetSocketAddress(a, address.getPort())).toList();
                    }).subscribe(promise::trySuccess, promise::tryFailure);
            }
        };
    }
}
