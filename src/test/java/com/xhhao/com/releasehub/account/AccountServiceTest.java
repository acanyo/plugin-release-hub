package com.xhhao.com.releasehub.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.provider.ProviderHttp;
import com.xhhao.com.releasehub.security.SecretStore;
import com.xhhao.com.releasehub.security.SecretAccess;
import com.xhhao.com.releasehub.service.ProjectService;
import com.xhhao.com.releasehub.support.MemoryStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AccountServiceTest {
    private final MemoryStore memory = new MemoryStore();
    private final SecretStore secrets = new SecretStore(memory.store);
    private final SecretAccess access = mock(SecretAccess.class);
    private final ProjectCredentials credentials = new ProjectCredentials(memory.store, secrets);
    private final RepositoryCatalog catalog = mock(RepositoryCatalog.class);
    private final AccountService accounts;
    private final ProjectService projects = new ProjectService(memory.store, secrets, credentials, access);
    private final AccountImportService imports;
    AccountServiceTest() {
        when(catalog.name()).thenReturn("GITHUB");
        when(catalog.identity(any(), anyString())).thenReturn(Mono.just(new RepositoryCatalog.Identity("42", "octocat")));
        when(access.requireRead(anyString())).thenReturn(Mono.empty());
        accounts = new AccountService(memory.store, secrets, access, List.of(catalog));
        imports = new AccountImportService(accounts, projects, memory.store);
    }
    private AccountService.AccountView create() {
        secrets.put("first-secret", "token", "first-token").block();
        return accounts.save(new AccountService.AccountInput(null, "个人账号", "GITHUB", "", "first-secret")).block();
    }
    private Project.Spec spec(String account) {
        var s = new Project.Spec(); s.setProvider("GITHUB"); s.setRepositoryUrl("https://github.com/org/app");
        s.setDisplayName("App"); s.setAccountName(account); return s;
    }
    private RepositoryCatalog.RemoteRepository remote(String path) {
        return new RepositoryCatalog.RemoteRepository(path, "App", "Description", "https://github.com/" + path, false, false);
    }
    @Test void enablingHaloNameDetectionClearsCacheAndKeepsFallback() {
        var p = projects.save(new ProjectService.Input(null, spec(""), "")).block();
        p.getSpec().setAutoDetectName(false); p.getStatus().setHaloDisplayName("已识别名称");
        p.getStatus().setHaloNameCheckedAt(java.time.Instant.now()); p = memory.client.update(p).block();
        var input = p.getSpec(); input.setAutoDetectName(true);
        var saved = projects.save(new ProjectService.Input(p.getMetadata().getName(), input, "")).block();
        assertNull(saved.getStatus().getHaloNameCheckedAt()); assertEquals("App", saved.getSpec().getDisplayName());
        assertEquals("已识别名称", saved.displayName());
    }
    @Test void persistsCredentialOnlyInSecretAndReturnsSafeView() throws Exception {
        var a = create();
        var stored = memory.get(RepositoryAccount.class, a.name());
        assertEquals("first-token", secrets.read(stored.getSpec().getCredentialSecret(), "token").block());
        String response = new ObjectMapper().findAndRegisterModules().writeValueAsString(accounts.list().block());
        assertFalse(response.contains("first-token")); assertTrue(response.contains("first-secret"));
        assertEquals("octocat", a.username()); assertTrue(a.hasToken());
    }
    @Test void invalidTokenDoesNotLeaveAccountOrDeleteSelectedSecret() {
        when(catalog.identity(any(), anyString())).thenReturn(Mono.error(new ProviderHttp.ProviderFailure(401, 0)));
        var error = assertThrows(IllegalArgumentException.class, this::create);
        assertTrue(error.getMessage().contains("Token 无效"));
        assertEquals(0, memory.store.all(RepositoryAccount.class).count().block());
        assertEquals(1, memory.store.all(run.halo.app.extension.Secret.class).count().block());
    }
    @Test void duplicateAccountAndDifferentIdentityAreRejected() {
        var a = create(); assertThrows(IllegalArgumentException.class, this::create);
        when(catalog.identity(any(), eq("other-token"))).thenReturn(Mono.just(new RepositoryCatalog.Identity("99", "other")));
        secrets.put("other-secret", "token", "other-token").block();
        assertThrows(IllegalArgumentException.class, () -> accounts.save(new AccountService.AccountInput(a.name(), "账号", "GITHUB", "", "other-secret")).block());
        assertEquals("first-token", credentials.token(spec(a.name())).block());
    }
    @Test void tokenRotationAndBlankPreservationReachExistingProjects() {
        var a = create(); var p = projects.save(new ProjectService.Input(null, spec(a.name()), "")).block();
        secrets.put("second-secret", "token", "second-token").block();
        accounts.save(new AccountService.AccountInput(a.name(), "新名称", "GITHUB", "", "second-secret")).block();
        assertEquals("second-token", credentials.token(p.getSpec()).block());
        accounts.save(new AccountService.AccountInput(a.name(), "再次改名", "GITHUB", "", "")).block();
        assertEquals("second-token", credentials.token(p.getSpec()).block());
        assertEquals(1, accounts.list().block().getFirst().linkedProjects());
        assertTrue(p.getSpec().getCredentialSecret().isBlank());
    }
    @Test void synchronizationLoadsLatestAccountTokenAndNeverFallsBackIfAccountIsMissing() {
        var a = create(); var p = projects.save(new ProjectService.Input(null, spec(a.name()), "")).block();
        var provider = mock(com.xhhao.com.releasehub.provider.ReleaseProvider.class);
        when(provider.name()).thenReturn("GITHUB");
        when(provider.fetch(any(), anyString(), anyInt(), nullable(String.class))).thenReturn(Mono.just(
            new com.xhhao.com.releasehub.provider.ReleaseProvider.Page(List.of(), true, false, null)));
        var names = mock(com.xhhao.com.releasehub.halo.HaloNameService.class);
        when(names.detect(any(), anyString())).thenReturn(Mono.empty());
        var sync = new com.xhhao.com.releasehub.sync.SyncService(memory.store, credentials,
            new com.xhhao.com.releasehub.content.MarkdownRenderer(), List.of(provider), names);
        secrets.put(a.credentialSecret(), "token", "rotated-token").block();
        accounts.save(new AccountService.AccountInput(a.name(), "账号", "GITHUB", "", a.credentialSecret())).block();
        assertEquals("SUCCEEDED", sync.sync(p.getMetadata().getName()).block().getSpec().getState());
        verify(provider).fetch(any(), eq("rotated-token"), eq(1), nullable(String.class));
        memory.client.delete(memory.get(RepositoryAccount.class, a.name())).block();
        assertEquals("FAILED", sync.sync(p.getMetadata().getName()).block().getSpec().getState());
        verify(provider, times(1)).fetch(any(), anyString(), anyInt(), nullable(String.class));
    }
    @Test void importedSelectionIsBoundedAndUnknownAccountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> imports.importRepositories("missing",
            new AccountImportService.RepositoryImportInput(List.of("https://github.com/org/app"), 60, true)).block());
        assertThrows(IllegalArgumentException.class, () -> imports.importRepositories("missing",
            new AccountImportService.RepositoryImportInput(java.util.Collections.nCopies(51, "https://github.com/org/app"), 60, true)).block());
        assertThrows(IllegalArgumentException.class, () -> imports.importRepositories("missing",
            new AccountImportService.RepositoryImportInput(java.util.Collections.singletonList(null), 60, true)).block());
    }
    @Test void accountDeletionIsBlockedWhileLinkedAndKeepsSharedSecretAfterUnlink() {
        var a = create(); var p = projects.save(new ProjectService.Input(null, spec(a.name()), "")).block();
        assertThrows(IllegalArgumentException.class, () -> accounts.delete(a.name()).block());
        p.getSpec().setAccountName(""); projects.save(new ProjectService.Input(p.getMetadata().getName(), p.getSpec(), "")).block();
        accounts.delete(a.name()).block();
        assertEquals(0, memory.store.all(RepositoryAccount.class).count().block());
        assertEquals(1, memory.store.all(run.halo.app.extension.Secret.class).count().block());
    }
    @Test void projectsCannotUseAccountForAnotherPlatformOrArbitrarySecret() {
        var a = create(); var wrong = spec(a.name()); wrong.setProvider("GITEE"); wrong.setRepositoryUrl("https://gitee.com/org/app");
        assertThrows(IllegalArgumentException.class, () -> projects.save(new ProjectService.Input(null, wrong, "")).block());
        var independent = spec(""); independent.setCredentialSecret("another-plugins-secret");
        var saved = projects.save(new ProjectService.Input(null, independent, "")).block();
        assertEquals("", saved.getSpec().getCredentialSecret());
        assertEquals("", credentials.token(saved.getSpec()).block());
    }
    @Test void newSecretReferencesRequireAccessAndValidTokenField() {
        when(access.requireRead("forbidden-secret")).thenReturn(Mono.error(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> accounts.save(
            new AccountService.AccountInput(null, "账号", "GITHUB", "", "forbidden-secret")).block());
        secrets.put("missing-token", "password", "some-password").block();
        assertThrows(IllegalArgumentException.class, () -> accounts.save(
            new AccountService.AccountInput(null, "账号", "GITHUB", "", "missing-token")).block());
        secrets.put("empty-token", "token", " ").block();
        assertThrows(IllegalArgumentException.class, () -> accounts.save(
            new AccountService.AccountInput(null, "账号", "GITHUB", "", "empty-token")).block());
        verify(catalog, never()).identity(any(), anyString());
    }
    @Test void manualProjectCanSelectAndClearAuthorizedSecretWithoutCopyingIt() {
        secrets.put("manual-secret", "token", "manual-token").block();
        var p = projects.save(new ProjectService.Input(null, spec(""), "manual-secret")).block();
        assertEquals("manual-token", credentials.token(p.getSpec()).block());
        var cleared = projects.save(new ProjectService.Input(p.getMetadata().getName(), p.getSpec(), "")).block();
        assertEquals("", credentials.token(cleared.getSpec()).block());
        assertEquals("manual-token", secrets.read("manual-secret", "token").block());
        verify(access).requireRead("manual-secret");
    }
    @Test void importedRepositoriesUseAccountAndRepeatedImportIsIdempotent() {
        var a = create(); when(catalog.repository(any(), anyString(), eq("org/app"))).thenReturn(Mono.just(remote("org/app")));
        var input = new AccountImportService.RepositoryImportInput(List.of("https://github.com/org/app"), 30, false);
        var first = imports.importRepositories(a.name(), input).block().items().getFirst();
        assertEquals("IMPORTED", first.state());
        var p = memory.get(Project.class, first.projectName()); assertEquals(a.name(), p.getSpec().getAccountName());
        assertFalse(p.getSpec().isEnabled()); assertEquals(30, p.getSpec().getSyncMinutes());
        assertEquals("SKIPPED", imports.importRepositories(a.name(), input).block().items().getFirst().state());
        assertEquals(1, memory.store.all(Project.class).count().block());
        verify(catalog, times(1)).repository(any(), anyString(), eq("org/app"));
    }
    @Test void discoveryMarksLegacyRepositoryAsAddedWithoutCopyingCredentials() {
        var a = create(); var p = new Project(); p.setMetadata(HubStore.metadata("legacy-random-id")); p.setSpec(spec("")); memory.put(p);
        when(catalog.repositories(any(), anyString(), eq(2))).thenReturn(Mono.just(new RepositoryCatalog.RepositoryPage(List.of(remote("org/app")), true)));
        var found = accounts.repositories(a.name(), 2).block(); assertTrue(found.items().getFirst().added()); assertTrue(found.hasMore());
        var result = imports.importRepositories(a.name(), new AccountImportService.RepositoryImportInput(List.of("https://github.com/ORG/APP"), 60, true)).block();
        assertEquals("SKIPPED", result.items().getFirst().state()); verify(catalog, never()).repository(any(), anyString(), anyString());
    }
    @Test void partialImportReportsFailuresAndRejectsForeignHostBeforeSendingToken() {
        var a = create(); when(catalog.repository(any(), anyString(), eq("org/app"))).thenReturn(Mono.just(remote("org/app")));
        when(catalog.repository(any(), anyString(), eq("org/denied"))).thenReturn(Mono.error(new ProviderHttp.ProviderFailure(404, 0)));
        var result = imports.importRepositories(a.name(), new AccountImportService.RepositoryImportInput(List.of(
            "https://github.com/org/app", "https://github.com/org/denied", "https://attacker.example/org/repo"), 60, true)).block();
        assertEquals(List.of("IMPORTED", "FAILED", "FAILED"), result.items().stream().map(AccountImportService.RepositoryImportItem::state).toList());
        assertEquals(1, memory.store.all(Project.class).count().block());
        verify(catalog, times(2)).repository(any(), anyString(), anyString());
    }
    @Test void upstreamErrorsNeverExposeTokensOrResponseDetails() {
        var a = create(); when(catalog.repositories(any(), anyString(), anyInt()))
            .thenReturn(Mono.error(new IllegalStateException("https://example.com?access_token=first-token")));
        var error = assertThrows(IllegalArgumentException.class, () -> accounts.repositories(a.name(), 1).block());
        assertFalse(error.getMessage().contains("first-token")); assertFalse(error.getMessage().contains("https://"));
    }
    @Test void platformAndInstanceValidationDisallowsCredentialRedirection() {
        assertEquals("https://gitlab.example.com", AccountAddress.normalize("GITLAB", "https://GitLab.Example.com/"));
        for (String url : List.of("https://attacker.example", "https://github.com/path", "https://github.com?token=secret", "http://github.com"))
            assertThrows(IllegalArgumentException.class, () -> AccountAddress.normalize("GITHUB", url));
        assertThrows(IllegalArgumentException.class, () -> AccountAddress.normalize("GITLAB", "https://localhost"));
    }
}
