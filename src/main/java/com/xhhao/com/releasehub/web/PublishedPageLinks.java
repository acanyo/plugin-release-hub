package com.xhhao.com.releasehub.web;

import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.security.SafeUrls;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Canonical links target the author's published Halo page instead of plugin-owned HTML routes. */
public final class PublishedPageLinks {
    private PublishedPageLinks() { }
    public static String page(HubSettings.General settings) {
        String page = SafeUrls.browserLink(settings.getPageUrl());
        if (page.isBlank()) throw new IllegalArgumentException("请先在插件设置中填写已发布的版本页面地址");
        return page;
    }
    public static String release(HubSettings.General settings, String name) { return query(page(settings), "rh-release", name); }
    public static String action(HubSettings.General settings, String action, String token) {
        return query(query(page(settings), "rh-action", action), "rh-token", token);
    }
    private static String query(String url, String key, String value) {
        return url + (url.contains("?") ? "&" : "?") + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
