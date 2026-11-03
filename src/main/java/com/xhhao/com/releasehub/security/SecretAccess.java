package com.xhhao.com.releasehub.security;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.core.user.service.RoleService;

/** A plugin resource permission must not bypass Halo's separate Secret read permission. */
@Component
public class SecretAccess {
    private final RoleService roles;
    public SecretAccess(RoleService roles) { this.roles = roles; }

    public Mono<Void> requireRead(String name) {
        return ReactiveSecurityContextHolder.getContext().map(context -> context.getAuthentication())
            .filter(Authentication::isAuthenticated).flatMap(auth -> {
                var roleNames = auth.getAuthorities().stream().map(a -> a.getAuthority())
                    .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).collect(Collectors.toSet());
                return roles.listDependenciesFlux(roleNames).filter(role -> role.getRules() != null).flatMapIterable(Role::getRules)
                    .any(rule -> matches(rule, name));
            }).defaultIfEmpty(false).flatMap(allowed -> allowed ? Mono.empty()
                : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "无权使用所选 Secret")));
    }
    private boolean matches(Role.PolicyRule rule, String name) {
        return includes(rule.getApiGroups(), "") && includes(rule.getResources(), "secrets")
            && includes(rule.getVerbs(), "get")
            && (rule.getResourceNames().length == 0 || Arrays.asList(rule.getResourceNames()).contains(name));
    }
    private boolean includes(String[] values, String expected) {
        return Arrays.stream(values).anyMatch(value -> value.equals("*") || value.equals(expected));
    }
}
