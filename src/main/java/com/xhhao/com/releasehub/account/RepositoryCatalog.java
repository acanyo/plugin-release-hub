package com.xhhao.com.releasehub.account;

import com.xhhao.com.releasehub.model.RepositoryAccount;
import java.util.List;
import reactor.core.publisher.Mono;

/** Platform repository discovery is separate from release synchronization. */
public interface RepositoryCatalog {
    int PAGE_SIZE = 50;
    String name();
    Mono<Identity> identity(RepositoryAccount.Spec account, String token);
    Mono<RepositoryPage> repositories(RepositoryAccount.Spec account, String token, int page);
    Mono<RemoteRepository> repository(RepositoryAccount.Spec account, String token, String path);

    record Identity(String id, String username) { }
    record RemoteRepository(String path, String displayName, String description, String url,
                            boolean privateRepository, boolean archived) { }
    record RepositoryPage(List<RemoteRepository> items, boolean hasMore) { }
}
