package com.xhhao.com.releasehub.content;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class MarkdownRenderer {
    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();
    public String render(String markdown) {
        var safe = Safelist.relaxed().removeTags("img").removeProtocols("a", "href", "ftp", "http")
            .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");
        return Jsoup.clean(renderer.render(parser.parse(markdown == null ? "" : markdown)), safe);
    }
    public String summary(String html) {
        String text = Jsoup.parse(html).text();
        return text.length() > 260 ? text.substring(0, 260) + "…" : text;
    }
}
