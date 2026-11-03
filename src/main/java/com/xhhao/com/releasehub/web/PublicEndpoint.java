package com.xhhao.com.releasehub.web;

import static com.xhhao.com.releasehub.web.ApiSupport.*;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.security.RequestThrottle;
import com.xhhao.com.releasehub.service.ReleaseService;
import com.xhhao.com.releasehub.subscription.SubscriptionService;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class PublicEndpoint implements CustomEndpoint {
    private final ReleaseService releases;
    private final SubscriptionService subscriptions;
    private final HubSettings settings;
    private final RequestThrottle throttle;
    public PublicEndpoint(ReleaseService releases, SubscriptionService subscriptions, HubSettings settings, RequestThrottle throttle) {
        this.releases = releases; this.subscriptions = subscriptions; this.settings = settings; this.throttle = throttle;
    }
    public record PublicSettings(String brandName, String description, String logoUrl, boolean emailEnabled,
                                 String primaryColor, String secondaryColor, String pageUrl) { }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .GET("projects", r -> releases.projects().flatMap(ApiSupport::ok), b -> operation(b, "listPublicProjects", "ReleaseHubPublic", ReleaseService.ProjectView[].class, null))
            .GET("releases", r -> releases.list(r.queryParam("project").orElse(""), r.queryParam("keyword").orElse(""),
                r.queryParam("channel").orElse("all"), integer(r, "page", 1), integer(r, "size", 10)).flatMap(ApiSupport::ok), b -> {
                    operation(b, "listPublicReleases", "ReleaseHubPublic", ReleaseService.ReleasePage.class, null);
                    for (String p : new String[]{"project", "keyword", "channel"}) query(b, p, String.class);
                    query(b, "page", Integer.class); query(b, "size", Integer.class);
                })
            .GET("releases/{name}", r -> releases.get(r.pathVariable("name")).flatMap(ApiSupport::ok),
                b -> operation(b, "getPublicRelease", "ReleaseHubPublic", ReleaseService.ReleaseView.class, null, "name"))
            .GET("settings", r -> Mono.zip(settings.general(), settings.mail(), settings.appearance()).map(t -> new PublicSettings(t.getT1().getBrandName(),
                t.getT1().getDescription(), com.xhhao.com.releasehub.security.SafeUrls.browserLink(t.getT1().getLogoUrl()), t.getT2().isEnabled(),
                t.getT3().primary(), t.getT3().secondary(), com.xhhao.com.releasehub.security.SafeUrls.browserLink(t.getT1().getPageUrl()))).flatMap(ApiSupport::ok),
                b -> operation(b, "getReleaseHubSettings", "ReleaseHubPublic", PublicSettings.class, null))
            .POST("subscriptions", r -> {
                limit(r);
                return body(r, SubscriptionService.EmailInput.class).flatMap(subscriptions::subscribe)
                    .then(done("如邮箱尚未订阅，我们将发送一封确认邮件，请检查收件箱。"));
            }, b -> operation(b, "subscribeReleaseEmail", "ReleaseHubPublic", Result.class, SubscriptionService.EmailInput.class))
            .POST("subscriptions/verify", r -> {
                limit(r); return body(r, TokenInput.class).flatMap(i -> subscriptions.action(i.token(), "verify")).then(done("订阅已确认"));
            }, b -> operation(b, "verifyReleaseEmail", "ReleaseHubPublic", Result.class, TokenInput.class))
            .POST("subscriptions/unsubscribe", r -> {
                limit(r); return body(r, TokenInput.class).flatMap(i -> subscriptions.action(i.token(), "unsubscribe")).then(done("已取消订阅"));
            }, b -> operation(b, "unsubscribeReleaseEmail", "ReleaseHubPublic", Result.class, TokenInput.class))
            .onError(Throwable.class, ApiSupport::failure).build();
    }
    private void limit(ServerRequest r) {
        String address = r.remoteAddress().map(a -> a.getAddress().getHostAddress()).orElse("unknown");
        throttle.check("public:" + address, 10, 60000);
    }
    @Override public GroupVersion groupVersion() { return new GroupVersion("api.release-hub.xhhao.com", "v1alpha1"); }
}
