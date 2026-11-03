package com.xhhao.com.releasehub.web;

import static com.xhhao.com.releasehub.web.ApiSupport.*;
import com.xhhao.com.releasehub.account.AccountService;
import com.xhhao.com.releasehub.account.AccountImportService;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class AccountEndpoint implements CustomEndpoint {
    private final AccountService accounts;
    private final AccountImportService imports;
    public AccountEndpoint(AccountService accounts, AccountImportService imports) { this.accounts = accounts; this.imports = imports; }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .GET("accounts", r -> accounts.list().flatMap(ApiSupport::ok),
                b -> operation(b, "listRepositoryAccounts", "ReleaseHubAdmin", AccountService.AccountView[].class, null))
            .POST("accounts", r -> body(r, AccountService.AccountInput.class).flatMap(accounts::save).flatMap(ApiSupport::ok),
                b -> operation(b, "saveRepositoryAccount", "ReleaseHubAdmin", AccountService.AccountView.class, AccountService.AccountInput.class))
            .DELETE("accounts/{name}", r -> accounts.delete(r.pathVariable("name")).then(done("仓库账号已删除")),
                b -> operation(b, "removeReleaseHubAccount", "ReleaseHubAdmin", Result.class, null, "name"))
            .GET("accounts/{name}/repositories", r -> accounts.repositories(r.pathVariable("name"), integer(r, "page", 1)).flatMap(ApiSupport::ok), b -> {
                operation(b, "listAccountRepositories", "ReleaseHubAdmin", AccountService.RepositoryPageView.class, null, "name");
                query(b, "page", Integer.class);
            })
            .POST("accounts/{name}/import", r -> body(r, AccountImportService.RepositoryImportInput.class)
                .flatMap(input -> imports.importRepositories(r.pathVariable("name"), input)).flatMap(ApiSupport::ok),
                b -> operation(b, "importAccountRepositories", "ReleaseHubAdmin", AccountImportService.RepositoryImportResult.class,
                    AccountImportService.RepositoryImportInput.class, "name"))
            .onError(Throwable.class, ApiSupport::failure).build();
    }
    @Override public GroupVersion groupVersion() { return new GroupVersion("console.api.release-hub.xhhao.com", "v1alpha1"); }
}
