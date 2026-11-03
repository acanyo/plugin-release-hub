package com.xhhao.com.releasehub.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.com.releasehub.model.RepositoryAccount;
import com.xhhao.com.releasehub.provider.ProviderHttp;
import java.net.URI;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

class CatalogTest {
    private ProviderHttp http(String json) throws Exception {
        var http = mock(ProviderHttp.class);
        when(http.get(any(), any())).thenReturn(Mono.just(new ProviderHttp.Response(new ObjectMapper().readTree(json), false, null)));
        return http;
    }
    private RepositoryAccount.Spec spec(String provider, String instance) {
        var s = new RepositoryAccount.Spec(); s.setProvider(provider); s.setInstanceUrl(instance); return s;
    }
    @Test void giteeUsesCanonicalWebPathInsteadOfTranslatedFullName() throws Exception {
        var http = http("""
            [{"id":1,"name":"仓库名称","full_name":"组织 / 仓库名称","html_url":"https://gitee.com/team/app","private":true}]
            """);
        var r = new GiteeCatalog(http).repositories(spec("GITEE", "https://gitee.com"), "t oken", 3).block();
        assertEquals("team/app", r.items().getFirst().path()); assertTrue(r.items().getFirst().privateRepository());
        verify(http).get(argThat(u -> u.getHost().equals("gitee.com") && u.getQuery().contains("page=3") && u.toString().contains("access_token=t%20oken")), any());
    }
    @Test void gitlabSupportsNestedNamespaceAndUsesMembershipFilter() throws Exception {
        var http = http("""
            [{"id":1,"name":"App","path_with_namespace":"org/team/app","visibility":"internal","archived":true}]
            """);
        var catalog = new GitLabCatalog(http); var a = spec("GITLAB", "https://gitlab.example.com");
        var r = catalog.repositories(a, "token", 1).block(); assertEquals("org/team/app", r.items().getFirst().path());
        assertTrue(r.items().getFirst().privateRepository()); assertTrue(r.items().getFirst().archived());
        verify(http).get(argThat(u -> u.getQuery().contains("membership=true")), any());
    }
    @Test void cnbUsesDocumentedCaseSensitiveVisibilityAndIntegerArchiveState() throws Exception {
        var http = http("""
            [{"id":"1","name":"App","path":"org/team/app","visibility_level":"Public","status":1},
             {"id":"2","name":"Private","path":"org/private","visibility_level":"Private","status":0}]
            """);
        var r = new CnbCatalog(http).repositories(spec("CNB", "https://cnb.cool"), "token", 4).block();
        assertFalse(r.items().getFirst().privateRepository()); assertTrue(r.items().getFirst().archived());
        assertTrue(r.items().get(1).privateRepository()); assertFalse(r.hasMore());
        verify(http).get(argThat(u -> u.getHost().equals("api.cnb.cool") && u.getQuery().contains("role=Guest") && u.getQuery().contains("page_size=50")), any());
    }
    @Test void gitlabAndCnbIdentityRequestsRemainOnTheirOwnPlatforms() throws Exception {
        var gitlab = http("{\"id\":1,\"username\":\"gitlab-user\"}");
        new GitLabCatalog(gitlab).identity(spec("GITLAB", "https://gitlab.example.com"), "lab-token").block();
        verify(gitlab).get(eq(URI.create("https://gitlab.example.com/api/v4/user")), argThat(c -> {
            var h = new HttpHeaders(); c.accept(h); return "lab-token".equals(h.getFirst("PRIVATE-TOKEN"));
        }));
        var cnb = http("{\"id\":\"cnb-id\",\"username\":\"cnb-user\"}");
        new CnbCatalog(cnb).identity(spec("CNB", "https://cnb.cool"), "cnb-token").block();
        verify(cnb).get(eq(URI.create("https://api.cnb.cool/user")), argThat(c -> {
            var h = new HttpHeaders(); c.accept(h); return "Bearer cnb-token".equals(h.getFirst("Authorization"));
        }));
    }
}
