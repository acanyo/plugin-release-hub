<script lang="ts">
    import { onMount } from 'svelte'
    import {
        publicClient,
        type ProjectView,
        type ReleaseView,
        type PublicSettings,
    } from '../api/client'
    import { errorMessage } from '../shared/format'
    import { paletteStyle } from './appearance/palette'
    import Icon from '../shared/Icon.svelte'
    import EmptyState from '../shared/EmptyState.svelte'
    import ReleaseOverview from './components/ReleaseOverview.svelte'
    import SegmentedControl from './components/SegmentedControl.svelte'
    import ReleaseViewTabs from './components/ReleaseViewTabs.svelte'
    import ProjectSelect from './components/ProjectSelect.svelte'
    import ReleaseTable from './components/ReleaseTable.svelte'
    import ReleaseSkeleton from './components/ReleaseSkeleton.svelte'
    import SubscriptionDialog from './SubscriptionDialog.svelte'
    import ConfirmAction from './ConfirmAction.svelte'
    import ReleaseEntry from './ReleaseEntry.svelte'
    import { reveal } from './motion/reveal'
    import './frontend.css'

    const api = publicClient()
    const route = location.pathname.split('/').filter(Boolean)
    const params = new URLSearchParams(location.search)
    const detailId = route[1] === 'versions' ? route[2] : undefined
    const tokenAction = route[1] === 'verify' || route[1] === 'unsubscribe'
    let project = $state(route[1] === 'projects' ? route[2] || '' : params.get('project') || '')
    let view = $state(params.get('view') === 'list' ? 'list' : 'timeline')
    let projects = $state<ProjectView[]>([]),
        items = $state<ReleaseView[]>([])
    let settings = $state<PublicSettings>({
        brandName: '版本中心',
        description: '每一次更新，都在这里。',
        emailEnabled: false,
    })
    let total = $state(0),
        page = $state(Math.min(100000, Math.max(1, Math.trunc(Number(params.get('page'))) || 1)))
    let channel = $state(
            ['all', 'stable', 'prerelease'].includes(params.get('channel') || '')
                ? params.get('channel')!
                : 'all',
        ),
        keyword = $state(params.get('keyword') || '')
    let loading = $state(true),
        error = $state(''),
        subscribe = $state(false),
        dark = $state(false)
    let overview = $state({ total: 0, stable: 0, prerelease: 0 }),
        overviewLoading = $state(true),
        contentKey = $state(0)
    const selected = $derived(projects.find((p) => p.name === project))
    const pageTitle = $derived(
        detailId && items[0]
            ? `${items[0].tag} · ${items[0].projectName}`
            : view === 'list'
              ? '所有版本'
              : selected
                ? `${selected.displayName} 更新日志`
                : '更新日志',
    )
    let sequence = 0,
        overviewSequence = 0

    async function load() {
        const request = ++sequence
        loading = true
        error = ''
        try {
            if (detailId) {
                const { data } = await api.getPublicRelease({ name: detailId })
                if (request !== sequence) return
                items = [data]
                total = 1
            } else {
                const { data } = await api.listPublicReleases({
                    project,
                    keyword,
                    channel,
                    page,
                    size: 10,
                })
                if (request !== sequence) return
                items = data.items || []
                total = data.total || 0
            }
            contentKey++
        } catch (e) {
            if (request === sequence) error = errorMessage(e)
        } finally {
            if (request === sequence) loading = false
        }
    }
    async function loadOverview() {
        const request = ++overviewSequence
        overviewLoading = true
        try {
            const pages = await Promise.all(
                ['all', 'stable', 'prerelease'].map((channel) =>
                    api.listPublicReleases({ project, channel, page: 1, size: 1 }),
                ),
            )
            if (request === overviewSequence)
                overview = {
                    total: pages[0]?.data.total || 0,
                    stable: pages[1]?.data.total || 0,
                    prerelease: pages[2]?.data.total || 0,
                }
        } catch {
            /* The version list shows any actionable connectivity error. */
        } finally {
            if (request === overviewSequence) overviewLoading = false
        }
    }
    function syncUrl() {
        const query = new URLSearchParams()
        if (view === 'list') query.set('view', 'list')
        if (keyword) query.set('keyword', keyword)
        if (channel !== 'all') query.set('channel', channel)
        if (page > 1) query.set('page', String(page))
        const path = project ? `/releases/projects/${encodeURIComponent(project)}` : '/releases'
        history.replaceState(history.state, '', path + (query.size ? '?' + query.toString() : ''))
    }
    function navigate(next: string) {
        if (detailId || tokenAction) {
            location.assign(`/releases${next === 'list' ? '?view=list' : ''}`)
            return
        }
        if (view === next) return
        view = next
        syncUrl()
        contentKey++
    }
    function filter(next?: string) {
        if (next) channel = next
        page = 1
        syncUrl()
        void load()
    }
    function changeProject() {
        page = 1
        syncUrl()
        void load()
        void loadOverview()
    }
    function paginate(next: number) {
        page = next
        syncUrl()
        void load()
        document.getElementById('rh-release-results')?.scrollIntoView({
            behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
            block: 'start',
        })
    }
    onMount(() => {
        const media = matchMedia('(prefers-color-scheme: dark)')
        function updateTheme() {
            const hosts = [document.documentElement, document.body]
            if (
                hosts.some(
                    (host) => host.classList.contains('dark') || host.dataset.theme === 'dark',
                )
            )
                dark = true
            else if (
                hosts.some(
                    (host) => host.classList.contains('light') || host.dataset.theme === 'light',
                )
            )
                dark = false
            else dark = media.matches
        }
        updateTheme()
        const themeObserver = new MutationObserver(updateTheme)
        for (const host of [document.documentElement, document.body])
            themeObserver.observe(host, {
                attributes: true,
                attributeFilter: ['class', 'data-theme'],
            })
        media.addEventListener('change', updateTheme)
        Promise.all([api.listPublicProjects(), api.getReleaseHubSettings()])
            .then(([p, s]) => {
                projects = p.data
                settings = s.data
            })
            .catch((e) => (error = errorMessage(e)))
            .finally(() => {
                if (!tokenAction) {
                    void load()
                    if (!detailId) void loadOverview()
                } else loading = false
            })
        const pop = () => {
            if (detailId || tokenAction) return
            const parts = location.pathname.split('/').filter(Boolean),
                query = new URLSearchParams(location.search)
            project = parts[1] === 'projects' ? parts[2] || '' : ''
            view = query.get('view') === 'list' ? 'list' : 'timeline'
            keyword = query.get('keyword') || ''
            channel = query.get('channel') || 'all'
            page = Math.max(1, Math.trunc(Number(query.get('page'))) || 1)
            void load()
            void loadOverview()
        }
        addEventListener('popstate', pop)
        return () => {
            removeEventListener('popstate', pop)
            media.removeEventListener('change', updateTheme)
            themeObserver.disconnect()
            sequence++
            overviewSequence++
        }
    })
</script>

<svelte:head><title>{pageTitle} · {settings.brandName}</title></svelte:head>
<div
    class="rh-root rh-public"
    data-theme={dark ? 'dark' : 'light'}
    style={paletteStyle(settings.primaryColor, settings.secondaryColor, dark)}
>
    <main class="rh-shell">
        {#if tokenAction}<ConfirmAction unsubscribe={route[1] === 'unsubscribe'} />
        {:else if detailId}
            <div class="rh-detail-nav" use:reveal={{ distance: 8 }}>
                <a
                    class="rh-back"
                    href={items[0]?.project
                        ? `/releases/projects/${items[0].project}`
                        : '/releases'}><Icon name="back" size={15} />返回更新日志</a
                ><button class="rh-quiet-button" onclick={() => (subscribe = true)}
                    ><Icon name="bell" size={15} />订阅这个项目</button
                >
            </div>
            {#if error}<EmptyState icon="info" title="暂时无法加载版本" description={error}
                    ><button class="rh-secondary" onclick={load}>重新加载</button></EmptyState
                >{:else if loading}<ReleaseSkeleton />{:else if items[0]}<div
                    class="rh-detail-container"
                >
                    <ReleaseEntry release={items[0]} detail />
                </div>{/if}
        {:else}
            <ReleaseOverview
                {...overview}
                projectCount={projects.length}
                loading={overviewLoading}
            />
            <div id="rh-release-results" class="rh-release-workspace">
                <div class="rh-page-heading" use:reveal={{ delay: 100, distance: 10 }}>
                    <div>
                        <h1>
                            {selected?.displayName || '版本中心'}
                        </h1>
                        <p>{selected?.description || settings.description}</p>
                    </div>
                    <button class="rh-subscribe-link" onclick={() => (subscribe = true)}
                        ><Icon name="rss" size={16} />订阅更新<Icon
                            name="arrow"
                            size={13}
                        /></button
                    >
                </div>
                <ReleaseViewTabs value={view} change={navigate} />
                <div class="rh-filter-bar" use:reveal={{ delay: 140, distance: 8 }}>
                    <SegmentedControl
                        label="版本类型"
                        options={[
                            { value: 'all', label: '全部版本' },
                            { value: 'stable', label: '正式版' },
                            { value: 'prerelease', label: '预发布' },
                        ]}
                        value={channel}
                        change={filter}
                    />
                    <div class="rh-filter-fields">
                        <ProjectSelect
                            {projects}
                            value={project}
                            change={(next) => {
                                project = next
                                changeProject()
                            }}
                        />
                        <form
                            class="rh-search"
                            onsubmit={(event) => {
                                event.preventDefault()
                                filter()
                            }}
                        >
                            <Icon name="search" size={16} /><input
                                type="search"
                                bind:value={keyword}
                                aria-label="搜索版本"
                                placeholder="搜索版本或更新内容"
                                maxlength="200"
                            /><kbd>↵</kbd>
                        </form>
                    </div>
                </div>
                <div class="rh-results-meta">
                    <span
                        >{keyword
                            ? `“${keyword}” 的搜索结果`
                            : view === 'list'
                              ? '版本与下载'
                              : '每一个版本的进展与改进'}</span
                    ><span
                        >{#if loading && items.length}<span class="rh-spinner"
                            ></span>正在更新{:else}{total.toLocaleString()} 个版本{/if}</span
                    >
                </div>
                <div
                    id="rh-release-view"
                    class="rh-results"
                    role="tabpanel"
                    aria-labelledby={`rh-view-${view}`}
                    tabindex="0"
                    class:rh-updating={loading && !!items.length}
                    aria-busy={loading}
                >
                    {#if error}<EmptyState icon="info" title="暂时无法加载版本" description={error}
                            ><button class="rh-secondary" onclick={load}>重新加载</button
                            ></EmptyState
                        >
                    {:else if loading && !items.length}<ReleaseSkeleton /><ReleaseSkeleton />
                    {:else if !items.length}<EmptyState
                            icon={keyword ? 'search' : 'box'}
                            title={keyword ? '没有找到匹配的版本' : '期待下一次发布'}
                            description={keyword
                                ? '试试其他版本号或更新关键词。'
                                : '新版本发布后会出现在这里。你可以先订阅，及时获取更新。'}
                            ><button class="rh-secondary" onclick={() => (subscribe = true)}
                                >订阅更新</button
                            ></EmptyState
                        >
                    {:else}{#key contentKey}{#if view === 'list'}<ReleaseTable
                                    releases={items}
                                />{:else}<div class="rh-timeline">
                                    {#each items as release, index (release.name)}<ReleaseEntry
                                            {release}
                                            latest={page === 1 && index === 0 && !keyword}
                                            {index}
                                        />{/each}
                                </div>{/if}{/key}{/if}
                </div>
                {#if total > 10}<nav class="rh-pagination" aria-label="版本分页">
                        <button
                            class="rh-secondary"
                            disabled={page <= 1 || loading}
                            onclick={() => paginate(page - 1)}
                            ><Icon name="back" size={14} />上一页</button
                        ><span>第 <strong>{page}</strong> / {Math.ceil(total / 10)} 页</span><button
                            class="rh-secondary"
                            disabled={page * 10 >= total || loading}
                            onclick={() => paginate(page + 1)}
                            >下一页<Icon name="chevron" size={14} /></button
                        >
                    </nav>{/if}
            </div>
        {/if}
        <footer class="rh-footer">
            <a class="rh-footer-brand" href="/releases"
                ><Icon name="box" size={17} />{settings.brandName}</a
            >
            <div>
                <a href="/releases/rss.xml">RSS</a><a href="/releases/atom.xml">Atom</a><span
                    >Powered by Halo</span
                >
            </div>
        </footer>
    </main>
    {#if subscribe}<SubscriptionDialog
            {projects}
            currentProject={project || (detailId ? items[0]?.project || '' : '')}
            emailEnabled={settings.emailEnabled || false}
            close={() => (subscribe = false)}
        />{/if}
</div>
