package com.xhhao.com.releasehub.web;

import com.xhhao.com.releasehub.feed.FeedService;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class FrontendRouter {
    private final FeedService feeds;
    public FrontendRouter(FeedService feeds) { this.feeds = feeds; }
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> releaseHubPages() {
        return RouterFunctions.route()
            .GET("/releases/rss.xml", r -> feed(r, false))
            .GET("/releases/atom.xml", r -> feed(r, true))
            .GET("/releases/projects/{project}/rss.xml", r -> feed(r, false))
            .GET("/releases/projects/{project}/atom.xml", r -> feed(r, true))
            .onError(Throwable.class, ApiSupport::failure).build();
    }
    private Mono<ServerResponse> feed(ServerRequest r, boolean atom) {
        String project = r.pathVariables().getOrDefault("project", r.queryParam("project").orElse(""));
        return feeds.render(project, Boolean.parseBoolean(r.queryParam("prerelease").orElse("false")), atom)
            .flatMap(xml -> ServerResponse.ok().contentType(MediaType.parseMediaType(atom ? "application/atom+xml;charset=UTF-8" : "application/rss+xml;charset=UTF-8"))
                .header("Cache-Control", "public, max-age=60").bodyValue(xml));
    }
}
