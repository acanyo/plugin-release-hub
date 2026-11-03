package com.xhhao.com.releasehub.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.provider.RepositoryAddress;
import java.util.function.Function;

final class CatalogJson {
    private CatalogJson() { }
    static String text(JsonNode node, String field) { return node.path(field).asText(""); }
    static RepositoryCatalog.Identity identity(JsonNode body, String usernameField) {
        String id = text(body, "id"), username = text(body, usernameField);
        if (id.isBlank() || username.isBlank()) throw new IllegalStateException("平台账号响应缺少身份信息");
        return new RepositoryCatalog.Identity(id, username);
    }
    static RepositoryCatalog.RemoteRepository repository(RepositoryAccount.Spec account, JsonNode node,
            String path, String name, boolean privateRepository, boolean archived) {
        var address = RepositoryAddress.parse(account.getProvider(), account.getInstanceUrl() + "/" + path);
        return new RepositoryCatalog.RemoteRepository(address.path(), name.isBlank() ? address.path() : name,
            text(node, "description"), address.web(), privateRepository, archived);
    }
    static RepositoryCatalog.RepositoryPage page(JsonNode body, Function<JsonNode, RepositoryCatalog.RemoteRepository> decode) {
        if (!body.isArray()) throw new IllegalStateException("平台仓库列表响应格式不正确");
        var result = new java.util.ArrayList<RepositoryCatalog.RemoteRepository>();
        body.forEach(node -> result.add(decode.apply(node)));
        return new RepositoryCatalog.RepositoryPage(result, body.size() >= RepositoryCatalog.PAGE_SIZE);
    }
}
