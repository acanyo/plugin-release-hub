package com.xhhao.com.releasehub.provider;

import com.xhhao.com.releasehub.model.Project;
import com.xhhao.com.releasehub.model.Release.Asset;
import java.time.Instant;
import java.util.List;
import reactor.core.publisher.Mono;

public interface ReleaseProvider {
    String name();
    Mono<Page> fetch(Project.Spec project, String token, int page, String etag);
    record RemoteRelease(String id, String tag, String title, String markdown, Instant publishedAt,
                         boolean prerelease, String sourceUrl, List<Asset> assets) { }
    record Page(List<RemoteRelease> releases, boolean last, boolean notModified, String etag) { }
}
