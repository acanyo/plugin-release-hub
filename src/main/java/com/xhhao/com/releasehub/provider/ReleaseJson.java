package com.xhhao.com.releasehub.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.xhhao.com.releasehub.model.Release.Asset;
import com.xhhao.com.releasehub.security.SafeUrls;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class ReleaseJson {
    private ReleaseJson() { }
    static String text(JsonNode node, String key) { return node.path(key).asText(""); }
    static Instant date(JsonNode node, String key) {
        String value = text(node, key);
        if (value.isBlank()) throw new IllegalArgumentException("平台版本缺少发布时间");
        return java.time.OffsetDateTime.parse(value).toInstant();
    }
    static boolean pre(String tag) {
        String version = tag.toLowerCase(java.util.Locale.ROOT);
        return version.matches("v?\\d+\\.\\d+\\.\\d+-[0-9a-z][0-9a-z.-]*(?:\\+[0-9a-z.-]+)?")
            || version.matches(".*[-.](?:alpha|beta|rc|preview|pre|dev|snapshot|nightly)(?:[.-][0-9a-z.-]+|[0-9]+)?");
    }
    static List<Asset> assets(JsonNode array, String urlKey) {
        var assets = new ArrayList<Asset>();
        if (array.isArray()) for (JsonNode a : array) {
            String url = SafeUrls.browserLink(text(a, urlKey));
            if (!url.isBlank()) assets.add(new Asset(text(a, "name"), url, Math.max(0, a.path("size").asLong(0))));
        }
        return assets;
    }
    static ReleaseProvider.RemoteRelease standard(JsonNode n, String fallbackUrl, boolean gitee) {
        String tag = text(n, "tag_name");
        if (tag.isBlank()) throw new IllegalArgumentException("平台版本缺少 tag_name");
        var assets = assets(gitee ? n.path("assets").path("links") : n.path("assets"), gitee ? "direct_asset_url" : "browser_download_url");
        if (gitee && assets.isEmpty()) assets = assets(n.path("assets").path("links"), "url");
        if (gitee && n.path("assets").isArray()) {
            assets = new ArrayList<>(assets);
            for (JsonNode a : n.path("assets")) {
                String link = SafeUrls.browserLink(a.isTextual() ? a.asText() : text(a, "browser_download_url"));
                if (!link.isBlank()) assets.add(new Asset(a.isTextual() ? "下载附件" : text(a, "name"), link, a.path("size").asLong(0)));
            }
        }
        String url = SafeUrls.browserLink(text(n, "html_url"));
        return new ReleaseProvider.RemoteRelease(text(n, "id").isBlank() ? tag : text(n, "id"), tag,
            text(n, "name").isBlank() ? tag : text(n, "name"), text(n, "body"),
            date(n, text(n, "published_at").isBlank() ? "created_at" : "published_at"),
            n.path("prerelease").asBoolean(false), url.isBlank() ? fallbackUrl : url, assets);
    }
}
