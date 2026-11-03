package com.xhhao.com.releasehub.halo;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HaloManifestParserTest {
    private final HaloManifestParser parser = new HaloManifestParser();
    private String plugin(String name) {
        return "apiVersion: plugin.halo.run/v1alpha1\nkind: Plugin\nmetadata:\n  name: example\nspec:\n  displayName: " + name + "\n  version: ${version}\n";
    }

    @Test void readsDeclaredDisplayNameWithoutDependingOnBuildTimeVersion() {
        var result = parser.parse("\ufeff# Halo plugin\n" + plugin("'灵感组件（LingUI）'"), "src/main/resources/plugin.yaml").orElseThrow();
        assertEquals("Plugin", result.kind()); assertEquals("灵感组件（LingUI）", result.displayName());
        assertEquals("src/main/resources/plugin.yaml", result.path());
    }
    @Test void readsThemeAndFoldedScalar() {
        var yaml = plugin(">-\n    清雅主题").replace("plugin.halo.run", "theme.halo.run").replace("kind: Plugin", "kind: Theme");
        assertEquals("清雅主题", parser.parse(yaml, "theme.yaml").orElseThrow().displayName());
    }
    @Test void ignoresOtherYamlAndMismatchedResourceKinds() {
        assertTrue(parser.parse("name: package-name", "plugin.yaml").isEmpty());
        assertTrue(parser.parse(plugin("Example"), "theme.yaml").isEmpty());
        assertTrue(parser.parse(plugin("Example").replace("plugin.halo.run", "other.example"), "plugin.yaml").isEmpty());
        assertTrue(parser.parse(plugin("Example").replace("  name: example", "  name: ''"), "plugin.yaml").isEmpty());
    }
    @Test void rejectsEmptyNonStringOverlongAndUnresolvedNames() {
        for (String name : java.util.List.of("''", "[]", "123", "'${displayName}'", "'" + "字".repeat(101) + "'", "\"line\\nnext\""))
            assertTrue(parser.parse(plugin(name), "plugin.yml").isEmpty(), name);
    }
    @Test void rejectsDuplicateKeysCustomTagsAliasesAndMalformedYaml() {
        for (String yaml : java.util.List.of(plugin("One") + "spec:\n  displayName: Two", "!!java.net.URL [https://example.com]",
            plugin("One") + "one: &one [1,2]\ntwo: *one\n", "[invalid", plugin("One") + "deep: " + "[".repeat(20) + "0" + "]".repeat(20)))
            assertTrue(parser.parse(yaml, "plugin.yaml").isEmpty());
    }
    @Test void boundsManifestSize() {
        assertTrue(parser.parse(plugin("Example") + "#".repeat(65536), "plugin.yaml").isEmpty());
    }
}
