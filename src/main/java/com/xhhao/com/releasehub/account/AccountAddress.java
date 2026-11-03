package com.xhhao.com.releasehub.account;

import com.xhhao.com.releasehub.model.Project;
import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.provider.RepositoryAddress;
import com.xhhao.com.releasehub.security.SafeUrls;
import java.util.Locale;

public final class AccountAddress {
    private AccountAddress() { }

    public static String normalize(String provider, String value) {
        String expected = switch (provider == null ? "" : provider) {
            case "GITHUB" -> "https://github.com";
            case "GITEE" -> "https://gitee.com";
            case "GITLAB" -> "https://gitlab.com";
            case "CNB" -> "https://cnb.cool";
            default -> throw new IllegalArgumentException("请选择支持的代码平台");
        };
        var uri = SafeUrls.https(value == null || value.isBlank() ? expected : value.strip());
        if (uri.getQuery() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
            throw new IllegalArgumentException("平台地址只填写站点根地址，不包含仓库路径或参数");
        String normalized = "https://" + uri.getHost().toLowerCase(Locale.ROOT);
        if (!"GITLAB".equals(provider) && !expected.equals(normalized))
            throw new IllegalArgumentException("该平台使用官方站点地址");
        return normalized;
    }

    public static void validateProject(RepositoryAccount account, Project.Spec project) {
        var spec = account.getSpec();
        if (!spec.getProvider().equals(project.getProvider())
            || !spec.getInstanceUrl().equals("https://" + RepositoryAddress.parse(project.getProvider(), project.getRepositoryUrl()).host()))
            throw new IllegalArgumentException("仓库与账号的平台或站点不匹配");
    }

    public static String key(String provider, String repositoryUrl) {
        String normalized = RepositoryAddress.parse(provider, repositoryUrl).web();
        return "GITHUB".equals(provider) || "GITEE".equals(provider) ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }
}
