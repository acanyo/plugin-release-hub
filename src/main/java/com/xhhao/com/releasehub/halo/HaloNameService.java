package com.xhhao.com.releasehub.halo;

import com.xhhao.com.releasehub.model.Project;
import com.xhhao.com.releasehub.provider.ProviderHttp;
import com.xhhao.com.releasehub.provider.RepositoryAddress;
import com.xhhao.com.releasehub.provider.RepositoryFiles;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class HaloNameService {
    private final RepositoryFiles files;
    private final HaloManifestParser parser;
    public HaloNameService(RepositoryFiles files, HaloManifestParser parser) { this.files = files; this.parser = parser; }
    public record Detection(HaloManifestParser.Manifest manifest, Instant checkedAt, String message) { }

    public Mono<Detection> detect(Project project, String token) {
        return Mono.defer(() -> {
            var checkedAt = project.getStatus().getHaloNameCheckedAt();
            if (!project.getSpec().isAutoDetectName() || checkedAt != null && checkedAt.isAfter(Instant.now().minus(Duration.ofDays(1))))
                return Mono.empty();
            var repo = RepositoryAddress.parse(project.getSpec().getProvider(), project.getSpec().getRepositoryUrl());
            var paths = repo.path().substring(repo.path().lastIndexOf('/') + 1).startsWith("theme-")
                ? List.of("theme.yaml", "theme.yml", "src/main/resources/plugin.yaml", "src/main/resources/plugin.yml", "plugin.yaml", "plugin.yml")
                : List.of("src/main/resources/plugin.yaml", "src/main/resources/plugin.yml", "plugin.yaml", "plugin.yml", "theme.yaml", "theme.yml");
            return Flux.fromIterable(paths).concatMap(path -> files.read(project.getSpec(), token, path)
                    .flatMap(content -> Mono.justOrEmpty(parser.parse(content, path))), 1)
                .next().map(manifest -> new Detection(manifest, Instant.now(), "已识别 Halo " + (manifest.kind().equals("Plugin") ? "插件" : "主题")))
                .switchIfEmpty(Mono.fromSupplier(() -> new Detection(null, Instant.now(), "未发现有效的 Halo 配置，使用备用名称")))
                .timeout(Duration.ofSeconds(20));
        }).onErrorResume(error -> Mono.just(new Detection(null, null,
            error instanceof ProviderHttp.ProviderFailure failure ? "名称识别暂不可用（HTTP " + failure.status + "），稍后重试"
                : "名称识别暂不可用，将保留当前名称并稍后重试")));
    }
}
