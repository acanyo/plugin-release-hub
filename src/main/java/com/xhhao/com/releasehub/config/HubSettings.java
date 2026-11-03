package com.xhhao.com.releasehub.config;

import lombok.Data;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import com.xhhao.com.releasehub.security.SafeUrls;

@Component
public class HubSettings {
    private final ReactiveSettingFetcher fetcher;
    public HubSettings(ReactiveSettingFetcher fetcher) { this.fetcher = fetcher; }
    public Mono<General> general() { return fetcher.fetch("general", General.class).defaultIfEmpty(new General()); }
    public Mono<Mail> mail() { return fetcher.fetch("mail", Mail.class).defaultIfEmpty(new Mail()); }
    public Mono<Appearance> appearance() {
        return fetcher.fetch("appearance", Appearance.class).defaultIfEmpty(new Appearance());
    }
    public Mono<GitHub> github() { return fetcher.fetch("github", GitHub.class).defaultIfEmpty(new GitHub()); }
    @Data
    public static class GitHub {
        private String apiBaseUrl = "";
    }
    @Data
    public static class Appearance {
        private String primaryColor = "#00c16a";
        private String secondaryColor = "#3b82f6";
        public String primary() { return color(primaryColor, "#00c16a"); }
        public String secondary() { return color(secondaryColor, "#3b82f6"); }
        private static String color(String value, String fallback) {
            if (value == null) return fallback;
            String normalized = value.strip().toLowerCase(java.util.Locale.ROOT);
            if (normalized.matches("#[0-9a-f]{6}")) return normalized;
            if (normalized.matches("#[0-9a-f]{3}")) {
                return "#" + normalized.charAt(1) + normalized.charAt(1)
                    + normalized.charAt(2) + normalized.charAt(2)
                    + normalized.charAt(3) + normalized.charAt(3);
            }
            return fallback;
        }
    }
    @Data
    public static class General {
        private String brandName = "版本中心";
        private String description = "每一次更新，都在这里。";
        private String logoUrl = "";
        private String siteUrl = "";
        private String pageUrl = "";
        public String baseUrl() {
            return SafeUrls.https(siteUrl).toASCIIString().replaceAll("/+$", "");
        }
    }
    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mail {
        private boolean enabled;
        private String subject = "{project} 发布了 {version}";
        private String introduction = "你订阅的项目有新版本了。";
    }
}
