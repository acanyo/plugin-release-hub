package com.xhhao.com.releasehub.service;

import com.xhhao.com.releasehub.model.*;
import java.util.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReleaseService {
    private final HubStore store;
    public ReleaseService(HubStore store) { this.store = store; }
    public record ProjectView(String name, String displayName, String description, String provider, String repositoryUrl) { }
    public record ReleaseView(String name, String project, String projectName, String tag, String title,
        String html, String summary, java.time.Instant publishedAt, boolean prerelease, String sourceUrl, List<Release.Asset> assets) { }
    public record ReleasePage(List<ReleaseView> items, long total, int page, int size) { }
    public Mono<List<ProjectView>> projects() {
        return store.all(Project.class).filter(p -> p.getSpec().isEnabled())
            .sort(Comparator.comparingInt(p -> p.getSpec().getSortOrder()))
            .map(p -> new ProjectView(p.getMetadata().getName(), p.displayName(), p.getSpec().getDescription(),
                p.getSpec().getProvider(), p.getSpec().getRepositoryUrl())).collectList();
    }
    public Mono<ReleasePage> list(String project, String keyword, String channel, int page, int size) {
        int safePage = Math.clamp(page, 1, 100000), safeSize = Math.clamp(size, 1, 50);
        String q = keyword == null ? "" : keyword.substring(0, Math.min(keyword.length(), 200)).toLowerCase(Locale.ROOT);
        return projects().flatMap(projects -> {
            var names = new HashMap<String, String>(); projects.forEach(p -> names.put(p.name(), p.displayName()));
            return store.all(Release.class).filter(r -> names.containsKey(r.getSpec().getProject()))
                .filter(r -> project == null || project.isBlank() || r.getSpec().getProject().equals(project))
                .filter(r -> !"stable".equals(channel) || !r.getSpec().isPrerelease())
                .filter(r -> !"prerelease".equals(channel) || r.getSpec().isPrerelease())
                .filter(r -> (r.getSpec().getTag() + " " + r.getSpec().getTitle() + " " + r.getSpec().getSummary()).toLowerCase(Locale.ROOT).contains(q))
                .sort(Comparator.<Release, java.time.Instant>comparing(r -> r.getSpec().getPublishedAt()).reversed()
                    .thenComparing(r -> r.getMetadata().getName()))
                .map(r -> view(r, names.get(r.getSpec().getProject()))).collectList()
                .map(items -> new ReleasePage(items.stream().skip((long) (safePage - 1) * safeSize).limit(safeSize).toList(), items.size(), safePage, safeSize));
        });
    }
    public Mono<ReleaseView> get(String id) {
        return store.client.get(Release.class, id).flatMap(r -> store.client.get(Project.class, r.getSpec().getProject())
            .filter(p -> p.getSpec().isEnabled()).switchIfEmpty(Mono.error(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)))
            .map(p -> view(r, p.displayName())));
    }
    private ReleaseView view(Release r, String projectName) {
        var s = r.getSpec();
        return new ReleaseView(r.getMetadata().getName(), s.getProject(), projectName, s.getTag(), s.getTitle(), s.getHtml(),
            s.getSummary(), s.getPublishedAt(), s.isPrerelease(), s.getSourceUrl(), s.getAssets());
    }
}
