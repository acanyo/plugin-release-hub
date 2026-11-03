package com.xhhao.com.releasehub.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.xhhao.com.releasehub.model.Project;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Read small files from the default branch through each platform's contents API. */
@Component
public class RepositoryFiles {
    public static final int MAX_FILE_BYTES = 64 * 1024;
    private final ProviderHttp http;
    private final GitHubApi github;
    public RepositoryFiles(ProviderHttp http, GitHubApi github) { this.http = http; this.github = github; }

    public Mono<String> read(Project.Spec project, String token, String path) {
        return Mono.defer(() -> {
            if (!path.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*")
                || java.util.Arrays.stream(path.split("/")).anyMatch(s -> s.equals(".") || s.equals("..")))
                return Mono.error(new IllegalArgumentException("仓库文件路径不正确"));
            var repo = RepositoryAddress.parse(project.getProvider(), project.getRepositoryUrl());
            Mono<ProviderHttp.Response> response = switch (project.getProvider()) {
                case "GITHUB" -> github.get("/repos/" + repo.path() + "/contents/" + path, token, null);
                case "GITEE" -> http.get(URI.create("https://gitee.com/api/v5/repos/" + repo.path() + "/contents/" + path
                    + (token.isBlank() ? "" : "?access_token=" + RepositoryAddress.encode(token))), h -> { });
                case "GITLAB" -> http.get(URI.create("https://" + repo.host() + "/api/v4/projects/" + RepositoryAddress.encode(repo.path())
                    + "/repository/files/" + RepositoryAddress.encode(path) + "?ref=HEAD"), h -> {
                        if (!token.isBlank()) h.set("PRIVATE-TOKEN", token);
                    });
                case "CNB" -> http.get(URI.create("https://api.cnb.cool/" + repo.path() + "/-/git/contents/" + path), h -> {
                    h.set("Accept", "application/vnd.cnb.api+json"); if (!token.isBlank()) h.setBearerAuth(token);
                });
                default -> Mono.error(new IllegalArgumentException("未知平台"));
            };
            return response.flatMap(r -> decode(r.body()));
        }).onErrorResume(ProviderHttp.ProviderFailure.class, failure -> failure.status == 404 ? Mono.empty() : Mono.error(failure));
    }

    private Mono<String> decode(JsonNode node) {
        return Mono.fromCallable(() -> {
            if (node == null || !node.isObject() || !"base64".equals(node.path("encoding").asText())
                || node.path("size").asLong(0) > MAX_FILE_BYTES
                || !node.path("content").isTextual()) return null;
            String type = node.path("type").asText("file");
            if (!type.equals("file") && !type.equals("blob")) return null; // Never follow symlinks or LFS URLs.
            String content = node.path("content").asText();
            if (content.length() > MAX_FILE_BYTES * 2) return null;
            byte[] bytes = Base64.getDecoder().decode(content.replaceAll("\\s", ""));
            if (bytes.length > MAX_FILE_BYTES) return null;
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        });
    }
}
