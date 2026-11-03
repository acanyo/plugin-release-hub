<script lang="ts">
    import { onMount, tick, untrack } from 'svelte'
    import type { ReleaseView } from '../api/client'
    import { date, bytes, safeLink } from '../shared/format'
    import Icon from '../shared/Icon.svelte'
    import { reveal } from './motion/reveal'
    let {
        release,
        detail = false,
        latest = false,
        index = 0,
    }: { release: ReleaseView; detail?: boolean; latest?: boolean; index?: number } = $props()
    let expanded = $state(untrack(() => detail)),
        canExpand = $state(false),
        content: HTMLDivElement,
        inner: HTMLDivElement
    const versionTitle = $derived(
        !release.title ||
            release.title === release.tag ||
            release.title === release.tag?.replace(/^v/, '')
            ? release.tag
            : release.title,
    )
    async function toggle() {
        const before = content.getBoundingClientRect().height
        expanded = !expanded
        await tick()
        const after = content.getBoundingClientRect().height
        if (!matchMedia('(prefers-reduced-motion: reduce)').matches)
            content.animate([{ height: `${before}px` }, { height: `${after}px` }], {
                duration: 320,
                easing: 'cubic-bezier(.2,.75,.25,1)',
            })
    }
    onMount(() => {
        const observer = new ResizeObserver(() => (canExpand = inner.scrollHeight > 264))
        observer.observe(inner)
        return () => observer.disconnect()
    })
</script>

<article
    class="rh-release"
    class:rh-detail={detail}
    class:rh-latest={latest}
    use:reveal={{ delay: Math.min(index * 40, 160) }}
>
    <div class="rh-release-date">
        <time datetime={release.publishedAt}>{date(release.publishedAt, true)}</time><span
            class="rh-timeline-dot"><span></span></span
        >{#if latest}<span class="rh-latest-label">最新发布</span>{/if}
    </div>
    <div class="rh-release-content">
        <div class="rh-release-meta">
            <a class="rh-project-label" href={`/releases/projects/${release.project}`}
                ><Icon name="repository" size={13} />{release.projectName}</a
            ><span class="rh-badge" class:rh-pre={release.prerelease}
                >{release.prerelease ? '预发布' : '正式版'}</span
            ><time class="rh-mobile-date" datetime={release.publishedAt}
                >{date(release.publishedAt, true)}</time
            >
        </div>
        <h2><a href={`/releases/versions/${release.name}`}>{versionTitle}</a></h2>
        {#if versionTitle !== release.tag}<div class="rh-version">
                <Icon name="tag" size={13} />{release.tag}
            </div>{/if}
        {#if detail}<div class="rh-detail-byline">
                <Icon name="calendar" size={14} />发布于 {date(release.publishedAt)}
            </div>{/if}
        <div
            class="rh-changelog-content"
            class:rh-clipped={!expanded && canExpand}
            class:rh-expanded={expanded}
            bind:this={content!}
        >
            <div class="rh-prose" bind:this={inner!} id={`rh-content-${release.name}`}>
                {@html release.html || '<p>此版本暂无更新说明。</p>'}
            </div>
        </div>
        {#if !detail && canExpand}<button
                class="rh-expand-button"
                aria-expanded={expanded}
                aria-controls={`rh-content-${release.name}`}
                onclick={toggle}
                >{expanded ? '收起日志' : '展开完整日志'}<span class:rh-rotated={expanded}
                    ><Icon name="chevron" size={14} /></span
                ></button
            >{/if}
        {#if detail && release.assets?.length}<section class="rh-assets">
                <div class="rh-assets-heading">
                    <h3><Icon name="download" size={16} />下载附件</h3>
                    <span>{release.assets.length} 个文件</span>
                </div>
                {#each release.assets as asset}{#if safeLink(asset.url)}<a
                            class="rh-asset"
                            href={safeLink(asset.url)}
                            target="_blank"
                            rel="noopener noreferrer"
                            ><span class="rh-file-icon"><Icon name="box" size={18} /></span><span
                                class="rh-file-name"
                                >{asset.name}<small>{bytes(asset.size) || '下载附件'}</small></span
                            ><Icon name="download" size={16} /></a
                        >{/if}{/each}
            </section>{/if}
        <div class="rh-release-links">
            {#if !detail}<a class="rh-text-link" href={`/releases/versions/${release.name}`}
                    >版本详情 <Icon name="arrow" size={14} /></a
                >{#if release.assets?.length}<a
                        class="rh-download-hint"
                        href={`/releases/versions/${release.name}`}
                        ><Icon name="download" size={14} />下载附件
                        <span>{release.assets.length}</span></a
                    >{/if}{:else if safeLink(release.sourceUrl)}<a
                    class="rh-text-link"
                    href={safeLink(release.sourceUrl)}
                    target="_blank"
                    rel="noopener noreferrer">在代码平台查看 <Icon name="arrow" size={14} /></a
                >{/if}
        </div>
    </div>
</article>
