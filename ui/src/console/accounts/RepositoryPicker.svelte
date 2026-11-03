<script lang="ts">
    import { onMount } from 'svelte'
    import type { ReleaseHubAdminApi } from '../../api/generated/api/release-hub-admin-api'
    import type { AccountView, RepositoryCandidate, RepositoryImportItem } from '../../api/client'
    import { errorMessage } from '../../shared/format'
    import Modal from '../../shared/Modal.svelte'
    import Icon from '../../shared/Icon.svelte'
    import EmptyState from '../../shared/EmptyState.svelte'
    import { platforms } from './platforms'

    let {
        api,
        account,
        close,
        imported,
    }: {
        api: ReleaseHubAdminApi
        account: AccountView
        close: () => void
        imported: () => Promise<void>
    } = $props()
    let repositories = $state<RepositoryCandidate[]>([]),
        selection = $state(new Set<string>())
    let page = $state(0),
        hasMore = $state(true),
        loading = $state(false),
        busy = $state(false)
    let keyword = $state(''),
        error = $state(''),
        syncMinutes = $state(60),
        enabled = $state(true)
    let results = $state<RepositoryImportItem[]>([])
    let alive = true
    const visible = $derived(
        repositories.filter((r) =>
            `${r.path} ${r.displayName} ${r.description}`
                .toLowerCase()
                .includes(keyword.toLowerCase().trim()),
        ),
    )
    const candidates = $derived(visible.filter((r) => !r.added && r.url))
    const allChecked = $derived(
        candidates.length > 0 && candidates.every((r) => selection.has(r.url!)),
    )
    const failures = $derived(
        new Map(results.filter((r) => r.state === 'FAILED').map((r) => [r.url, r.message])),
    )
    async function loadMore() {
        if (loading || busy || !hasMore) return
        loading = true
        error = ''
        try {
            const { data } = await api.listAccountRepositories({
                name: account.name!,
                page: page + 1,
            })
            if (!alive) return
            const existing = new Set(repositories.map((r) => r.url))
            repositories = [
                ...repositories,
                ...(data.items || []).filter((r) => !existing.has(r.url)),
            ]
            page = data.page || page + 1
            hasMore = !!data.hasMore
        } catch (e) {
            if (alive) error = errorMessage(e)
        } finally {
            if (alive) loading = false
        }
    }
    function toggle(url: string, checked: boolean) {
        const next = new Set(selection)
        if (checked) {
            if (next.size >= 50) {
                error = '每批最多添加 50 个仓库。'
                return
            }
            next.add(url)
        } else next.delete(url)
        selection = next
    }
    function toggleAll() {
        const next = new Set(selection)
        if (allChecked) candidates.forEach((r) => next.delete(r.url!))
        else
            for (const r of candidates) {
                if (next.size >= 50) break
                next.add(r.url!)
            }
        selection = next
    }
    async function submit(event: SubmitEvent) {
        event.preventDefault()
        if (!selection.size) return
        busy = true
        error = ''
        results = []
        try {
            const { data } = await api.importAccountRepositories({
                name: account.name!,
                repositoryImportInput: {
                    repositoryUrls: [...selection],
                    syncMinutes,
                    enabled,
                },
            })
            results = data.items || []
            const added = new Set(results.filter((r) => r.state !== 'FAILED').map((r) => r.url))
            repositories = repositories.map((r) => (added.has(r.url) ? { ...r, added: true } : r))
            selection = new Set([...selection].filter((url) => !added.has(url)))
            await imported()
        } catch (e) {
            error = errorMessage(e)
        } finally {
            busy = false
        }
    }
    onMount(() => {
        void loadMore()
        return () => {
            alive = false
        }
    })
</script>

<Modal
    title="选择要添加的仓库"
    description={`${platforms[account.provider || '']} · ${account.username}，列表仅包含此 Token 可访问的仓库。`}
    close={() => {
        if (!busy) close()
    }}
    size="wide"
    closeDisabled={busy}
>
    <form id="rh-repository-import" onsubmit={submit}>
        <label class="rh-console-search rh-repository-search"
            ><Icon name="search" size={16} /><input
                type="search"
                bind:value={keyword}
                aria-label="搜索可选仓库"
                placeholder="搜索已加载仓库"
            /></label
        >
        <div class="rh-repository-selection">
            <label class="rh-checkbox"
                ><input
                    type="checkbox"
                    checked={allChecked}
                    disabled={!candidates.length || busy}
                    onchange={toggleAll}
                />选择当前结果</label
            ><span>已加载 {repositories.length} 个 · 已选 {selection.size} / 50</span>
        </div>
        <div class="rh-repository-list" aria-busy={loading}>
            {#each visible as repository (repository.url)}
                <label class="rh-repository-choice" class:rh-repository-added={repository.added}>
                    <input
                        type="checkbox"
                        aria-label={`选择 ${repository.path}`}
                        checked={repository.added || selection.has(repository.url!)}
                        disabled={busy || repository.added}
                        onchange={(event) => toggle(repository.url!, event.currentTarget.checked)}
                    />
                    <span class="rh-repository-choice-body"
                        ><strong title={repository.path}>{repository.path}</strong><span
                            >{repository.description || repository.displayName}</span
                        >
                        {#if failures.has(repository.url)}<small class="rh-text-error"
                                >{failures.get(repository.url)}</small
                            >{/if}
                    </span>
                    <span class="rh-repository-tags"
                        >{#if repository.added}<span class="rh-status">已添加</span
                            >{/if}{#if repository.privateRepository}<span class="rh-status"
                                >私有</span
                            >{/if}{#if repository.archived}<span class="rh-status">已归档</span
                            >{/if}</span
                    >
                </label>
            {/each}
            {#if loading}<div class="rh-repository-loading">
                    <span class="rh-spinner"></span>正在查询仓库…
                </div>
            {:else if !visible.length}<EmptyState
                    icon="repository"
                    title={keyword ? '没有匹配的已加载仓库' : '没有可选仓库'}
                    description={hasMore
                        ? '可继续加载更多仓库，或调整搜索关键词。'
                        : '请检查 Token 的仓库范围和读取权限。'}
                />{/if}
        </div>
        {#if hasMore}<button
                type="button"
                class="rh-secondary rh-repository-more"
                disabled={loading || busy}
                onclick={loadMore}>{loading ? '正在加载…' : '加载更多仓库'}</button
            >{/if}
        {#if results.length}<p class="rh-inline-note" role="status">
                已添加 {results.filter((r) => r.state === 'IMPORTED').length} 个，跳过 {results.filter(
                    (r) => r.state === 'SKIPPED',
                ).length} 个，失败 {results.filter((r) => r.state === 'FAILED').length} 个。{#if failures.size}失败的仓库仍保留勾选，可重试。{/if}
            </p>{/if}
        <div class="rh-repository-import-settings">
            <label class="rh-field"
                >同步间隔（分钟）<input
                    type="number"
                    min="5"
                    max="10080"
                    required
                    bind:value={syncMinutes}
                    disabled={busy}
                /></label
            >
            <label class="rh-checkbox"
                ><input
                    type="checkbox"
                    bind:checked={enabled}
                    disabled={busy}
                />添加后启用项目</label
            >
        </div>
        <p class="rh-small rh-muted">
            启用后，该仓库的版本日志会在前台公开展示，私有仓库也适用。首次同步只导入历史版本。
        </p>
        {#if error}<p class="rh-error" role="alert"><Icon name="info" size={16} />{error}</p>{/if}
    </form>
    {#snippet footer()}<button type="button" class="rh-secondary" disabled={busy} onclick={close}
            >完成</button
        ><button
            class="rh-primary"
            type="submit"
            form="rh-repository-import"
            disabled={busy || !selection.size}
            >{#if busy}<span class="rh-spinner"></span>{/if}{busy
                ? '正在添加仓库…'
                : `添加选中的 ${selection.size} 个仓库`}</button
        >{/snippet}
</Modal>
