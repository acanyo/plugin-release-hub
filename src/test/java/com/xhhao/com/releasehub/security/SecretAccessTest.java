package com.xhhao.com.releasehub.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import run.halo.app.core.extension.Role;
import run.halo.app.core.user.service.RoleService;

class SecretAccessTest {
    private final RoleService roles = mock(RoleService.class);
    private final SecretAccess access = new SecretAccess(roles);
    private void rules(String[] resources, String[] names, String[] verbs) {
        var role = new Role(); role.setRules(List.of(new Role.PolicyRule(new String[]{""}, resources, names, null, verbs)));
        when(roles.listDependenciesFlux(anySet())).thenReturn(Flux.just(role));
    }
    private void check(String name) {
        var auth = new UsernamePasswordAuthenticationToken("manager", "", List.of(new SimpleGrantedAuthority("ROLE_secret-reader")));
        access.requireRead(name).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)).block();
    }
    @Test void pluginPermissionAloneCannotUseArbitrarySecret() {
        rules(new String[]{"accounts"}, new String[]{}, new String[]{"*"});
        assertThrows(ResponseStatusException.class, () -> check("smtp-password"));
    }
    @Test void namedSecretPermissionIsEnforced() {
        rules(new String[]{"secrets"}, new String[]{"repo-token"}, new String[]{"get"});
        assertDoesNotThrow(() -> check("repo-token"));
        assertThrows(ResponseStatusException.class, () -> check("smtp-password"));
    }
    @Test void wildcardSecretReadWorksAndUnauthenticatedReadFails() {
        rules(new String[]{"*"}, new String[]{}, new String[]{"*"});
        assertDoesNotThrow(() -> check("repo-token"));
        assertThrows(ResponseStatusException.class, () -> access.requireRead("repo-token").block());
    }
}
