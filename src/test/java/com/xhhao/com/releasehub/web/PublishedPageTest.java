package com.xhhao.com.releasehub.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.feed.FeedService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class PublishedPageTest {
    private HubSettings.General general(String url) { var settings = new HubSettings.General(); settings.setPageUrl(url); return settings; }
    @Test void detailAndActionsUsePublishedPageAndEncodeTokenSeparately() {
        var settings = general("https://example.com/my-updates");
        assertEquals("https://example.com/my-updates?rh-release=release-1", PublishedPageLinks.release(settings, "release-1"));
        assertEquals("https://example.com/my-updates?rh-action=verify&rh-token=a%26b%3Dc", PublishedPageLinks.action(settings, "verify", "a&b=c"));
    }
    @Test void retainsExistingPageQueryAndRejectsMissingOrUnsafePageAddress() {
        assertEquals("https://example.com/updates?lang=zh&rh-release=id", PublishedPageLinks.release(general("https://example.com/updates?lang=zh"), "id"));
        for (String url : java.util.List.of("", "javascript:alert(1)", "https://user:pass@example.com/updates", "https://example.com/updates#fragment"))
            assertThrows(IllegalArgumentException.class, () -> PublishedPageLinks.page(general(url)));
    }
    @Test void onlyFeedsAreServedAndHtmlRoutesAreNotRegistered() {
        var feeds = mock(FeedService.class); when(feeds.render("", false, false)).thenReturn(Mono.just("<rss version=\"2.0\"/>"));
        var client = WebTestClient.bindToRouterFunction(new FrontendRouter(feeds).releaseHubPages()).build();
        client.get().uri("/releases/rss.xml").exchange().expectStatus().isOk().expectHeader().contentType("application/rss+xml;charset=UTF-8");
        for (String path : java.util.List.of("/releases", "/releases/", "/releases/projects/project-1", "/releases/versions/release-1", "/releases/verify"))
            client.get().uri(path).exchange().expectStatus().isNotFound();
    }
}
