package com.xhhao.com.releasehub.feed;

import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.service.ReleaseService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FeedService {
    private final ReleaseService releases;
    private final HubSettings settings;
    public FeedService(ReleaseService releases, HubSettings settings) { this.releases = releases; this.settings = settings; }
    public Mono<String> render(String project, boolean prerelease, boolean atom) {
        return Mono.zip(settings.general(), releases.list(project, "", prerelease ? "all" : "stable", 1, 50), releases.projects())
            .map(tuple -> {
                var general = tuple.getT1(); String base = general.baseUrl();
                String title = general.getBrandName();
                if (!project.isBlank()) title += " · " + tuple.getT3().stream().filter(p -> p.name().equals(project))
                    .findFirst().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)).displayName();
                String self = base + "/releases/" + (atom ? "atom.xml" : "rss.xml") + "?project=" + project + "&prerelease=" + prerelease;
                var items = tuple.getT2().items();
                String updated = items.isEmpty() ? Instant.EPOCH.toString() : items.getFirst().publishedAt().toString();
                var out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                if (atom) {
                    out.append("<feed xmlns=\"http://www.w3.org/2005/Atom\"><id>").append(xml(base + "/releases" + (project.isBlank() ? "" : "/projects/" + project)))
                        .append("</id><title>").append(xml(title)).append("</title><updated>").append(updated)
                        .append("</updated><author><name>").append(xml(general.getBrandName())).append("</name></author><link rel=\"self\" href=\"")
                        .append(xml(self)).append("\"/><link href=\"").append(xml(base + "/releases")).append("\"/>");
                } else {
                    out.append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\"><channel><title>").append(xml(title))
                        .append("</title><link>").append(xml(base + "/releases")).append("</link><description>").append(xml(general.getDescription()))
                        .append("</description><atom:link href=\"").append(xml(self)).append("\" rel=\"self\" type=\"application/rss+xml\"/>");
                }
                for (var item : items) {
                    String url = base + "/releases/versions/" + item.name();
                    String label = item.projectName() + " " + item.tag();
                    if (atom) out.append("<entry><id>urn:release-hub:").append(item.name()).append("</id><title>").append(xml(label))
                        .append("</title><link href=\"").append(xml(url)).append("\"/><updated>").append(item.publishedAt())
                        .append("</updated><content type=\"html\">").append(xml(item.html())).append("</content></entry>");
                    else out.append("<item><guid isPermaLink=\"false\">urn:release-hub:").append(item.name()).append("</guid><title>")
                        .append(xml(label)).append("</title><link>").append(xml(url)).append("</link><pubDate>")
                        .append(DateTimeFormatter.RFC_1123_DATE_TIME.format(item.publishedAt().atZone(ZoneOffset.UTC)))
                        .append("</pubDate><description>").append(xml(item.html())).append("</description></item>");
                }
                return out.append(atom ? "</feed>" : "</channel></rss>").toString();
            });
    }
    static String xml(String value) {
        return (value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}
