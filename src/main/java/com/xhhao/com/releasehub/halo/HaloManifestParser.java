package com.xhhao.com.releasehub.halo;

import com.xhhao.com.releasehub.provider.RepositoryFiles;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Component
public class HaloManifestParser {
    public record Manifest(String kind, String displayName, String path) { }

    public Optional<Manifest> parse(String content, String path) {
        if (content == null || content.length() > RepositoryFiles.MAX_FILE_BYTES) return Optional.empty();
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(12);
        options.setCodePointLimit(RepositoryFiles.MAX_FILE_BYTES);
        try {
            Object document = new Yaml(new SafeConstructor(options)).load(content);
            if (!(document instanceof Map<?, ?> root)) return Optional.empty();
            String kind = path.endsWith("theme.yaml") || path.endsWith("theme.yml") ? "Theme" : "Plugin";
            String api = kind.equals("Theme") ? "theme.halo.run/v1alpha1" : "plugin.halo.run/v1alpha1";
            if (!kind.equals(root.get("kind")) || !api.equals(root.get("apiVersion"))
                || !(root.get("metadata") instanceof Map<?, ?> metadata)
                || !(metadata.get("name") instanceof String id) || id.isBlank()
                || !(root.get("spec") instanceof Map<?, ?> spec)
                || !(spec.get("displayName") instanceof String name)) return Optional.empty();
            name = name.strip();
            if (name.isBlank() || name.length() > 100 || name.contains("${") || name.codePoints().anyMatch(Character::isISOControl))
                return Optional.empty();
            return Optional.of(new Manifest(kind, name, path));
        } catch (RuntimeException ignored) {
            // A malformed or unrelated YAML file must not interrupt release synchronization.
            return Optional.empty();
        }
    }
}
