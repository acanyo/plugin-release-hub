package com.xhhao.com.releasehub.security;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Component
public class OutboundHttp {
    private final PublicAddressResolver resolver = new PublicAddressResolver();
    private final WebClient client;
    public WebClient client() { return client; }
    public OutboundHttp() {
        var http = HttpClient.create().followRedirect(false).resolver(resolver)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).responseTimeout(Duration.ofSeconds(20));
        client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(http))
            .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
            .defaultHeader("User-Agent", "Halo-Release-Hub/1.0")
            .build();
    }
    @jakarta.annotation.PreDestroy
    public void close() { resolver.close(); }
}
