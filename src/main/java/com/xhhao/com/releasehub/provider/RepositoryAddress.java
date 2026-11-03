package com.xhhao.com.releasehub.provider;

import com.xhhao.com.releasehub.security.SafeUrls;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public record RepositoryAddress(String host, String path) {
    public static RepositoryAddress parse(String platform, String value) {
        if (platform == null || value == null) throw invalid();
        URI uri = SafeUrls.https(value);
        if (uri.getQuery() != null) throw new IllegalArgumentException("仓库地址不能包含查询参数");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath().replaceAll("^/+|/+$", "").replaceFirst("\\.git$", "");
        String[] parts = path.split("/");
        if (parts.length < 2 || Arrays.stream(parts).anyMatch(p -> !p.matches("[A-Za-z0-9_.-]+")
            || p.equals(".") || p.equals(".."))) throw new IllegalArgumentException("仓库地址需要组织与仓库路径");
        switch (platform) {
            case "GITHUB" -> { if (!host.equals("github.com") || parts.length != 2) throw invalid(); }
            case "GITEE" -> { if (!host.equals("gitee.com") || parts.length != 2) throw invalid(); }
            case "CNB" -> { if (!host.equals("cnb.cool")) throw invalid(); }
            case "GITLAB" -> { }
            default -> throw invalid();
        }
        return new RepositoryAddress(host, path);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("平台与仓库地址不匹配"); }
    public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    public String web() { return "https://" + host + "/" + path; }
}
