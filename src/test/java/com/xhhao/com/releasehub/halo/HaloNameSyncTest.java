package com.xhhao.com.releasehub.halo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.xhhao.com.releasehub.account.ProjectCredentials;
import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.content.MarkdownRenderer;
import com.xhhao.com.releasehub.feed.FeedService;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.provider.*;
import com.xhhao.com.releasehub.security.SecretStore;
import com.xhhao.com.releasehub.service.ReleaseService;
import com.xhhao.com.releasehub.support.MemoryStore;
import com.xhhao.com.releasehub.sync.SyncService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class HaloNameSyncTest {
    private final MemoryStore memory = new MemoryStore();
    private final RepositoryFiles files = mock(RepositoryFiles.class);
    private final ReleaseProvider provider = mock(ReleaseProvider.class);
    private final HaloNameService names = new HaloNameService(files, new HaloManifestParser());
    private final SyncService sync;
    HaloNameSyncTest() {
        when(provider.name()).thenReturn("GITHUB");
        when(provider.fetch(any(), anyString(), anyInt(), nullable(String.class)))
            .thenReturn(Mono.just(new ReleaseProvider.Page(List.of(), true, true, "etag")));
        when(files.read(any(), anyString(), anyString())).thenReturn(Mono.empty());
        sync = new SyncService(memory.store, new ProjectCredentials(memory.store, new SecretStore(memory.store)), new MarkdownRenderer(), List.of(provider), names);
        var p = new Project(); p.setMetadata(HubStore.metadata("project-app")); p.setSpec(new Project.Spec());
        p.getSpec().setDisplayName("plugin-app"); p.getSpec().setProvider("GITHUB"); p.getSpec().setRepositoryUrl("https://github.com/org/plugin-app"); memory.put(p);
    }
    private void manifest(String path, String kind, String name) {
        when(files.read(any(), anyString(), eq(path))).thenReturn(Mono.just("apiVersion: " + (kind.equals("Plugin") ? "plugin" : "theme")
            + ".halo.run/v1alpha1\nkind: " + kind + "\nmetadata:\n  name: app\nspec:\n  displayName: '" + name + "'\n"));
    }
    private Project project() { return memory.get(Project.class, "project-app"); }
    @Test void detectsNameEvenWhenReleasesAreNotModifiedAndFeedsUseIt() {
        manifest("src/main/resources/plugin.yaml", "Plugin", "灵感组件");
        assertEquals("SUCCEEDED", sync.sync("project-app").block().getSpec().getState());
        var p = project(); assertEquals("灵感组件", p.displayName()); assertEquals("plugin-app", p.getSpec().getDisplayName());
        assertEquals("Plugin", p.getStatus().getHaloKind()); assertEquals("src/main/resources/plugin.yaml", p.getStatus().getHaloManifestPath());
        var releases = new ReleaseService(memory.store); assertEquals("灵感组件", releases.projects().block().getFirst().displayName());
        var settings = mock(HubSettings.class); var general = new HubSettings.General(); general.setSiteUrl("https://example.com");
        when(settings.general()).thenReturn(Mono.just(general));
        assertTrue(new FeedService(releases, settings).render("project-app", false, false).block().contains("灵感组件"));
    }
    @Test void findsThemeWithoutRelyingOnRepositoryNameAndCachesDiscovery() {
        manifest("theme.yaml", "Theme", "清雅主题"); sync.sync("project-app").block();
        assertEquals("清雅主题", project().displayName()); assertEquals("Theme", project().getStatus().getHaloKind());
        clearInvocations(files); sync.sync("project-app").block(); verifyNoInteractions(files);
    }
    @Test void fallsBackToRootPluginAndYmlFiles() {
        manifest("plugin.yml", "Plugin", "根目录插件"); sync.sync("project-app").block();
        assertEquals("根目录插件", project().displayName());
    }
    @Test void noManifestKeepsFallbackNameAndDoesNotFailReleaseSync() {
        assertEquals("SUCCEEDED", sync.sync("project-app").block().getSpec().getState());
        assertEquals("plugin-app", project().displayName()); assertNotNull(project().getStatus().getHaloNameCheckedAt());
    }
    @Test void disabledDetectionPreservesManualNamesAndMakesNoFileRequests() {
        var p = project(); p.getSpec().setAutoDetectName(false); p.getStatus().setHaloDisplayName("旧名称"); memory.client.update(p).block();
        sync.sync("project-app").block(); assertEquals("plugin-app", project().displayName()); verifyNoInteractions(files);
    }
    @Test void transientErrorsKeepPreviousNameAndDoNotCacheFailureOrFailSync() {
        var p = project(); p.getStatus().setHaloDisplayName("之前识别的名称"); memory.client.update(p).block();
        when(files.read(any(), anyString(), anyString())).thenReturn(Mono.error(new ProviderHttp.ProviderFailure(403, 300)));
        assertEquals("SUCCEEDED", sync.sync("project-app").block().getSpec().getState());
        assertEquals("之前识别的名称", project().displayName()); assertNull(project().getStatus().getHaloNameCheckedAt());
        assertTrue(project().getStatus().getHaloNameMessage().contains("HTTP 403"));
        verify(files, times(1)).read(any(), anyString(), anyString());
    }
    @Test void refreshesExpiredNamesAndReturnsToFallbackWhenManifestIsRemoved() {
        var p = project(); p.getStatus().setHaloDisplayName("旧名称"); p.getStatus().setHaloNameCheckedAt(Instant.now().minusSeconds(90000)); memory.client.update(p).block();
        manifest("src/main/resources/plugin.yaml", "Plugin", "新名称"); sync.sync("project-app").block(); assertEquals("新名称", project().displayName());
        p = project(); p.getStatus().setHaloNameCheckedAt(Instant.now().minusSeconds(90000)); memory.client.update(p).block();
        when(files.read(any(), anyString(), anyString())).thenReturn(Mono.empty()); sync.sync("project-app").block();
        assertEquals("plugin-app", project().displayName()); assertNull(project().getStatus().getHaloKind());
    }
}
