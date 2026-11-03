<script lang="ts">
    import type { ReleaseHubAdminApi } from '../api/generated/api/release-hub-admin-api'
    import type { Project, ReleaseProjectSpec, AccountView } from '../api/client'
    import Modal from '../shared/Modal.svelte'
    import Icon from '../shared/Icon.svelte'
    import StatusBadge from '../shared/StatusBadge.svelte'
    import SecretField from './halo-ui/SecretField.svelte'
    import EmptyState from '../shared/EmptyState.svelte'
    import { date, errorMessage, safeLink } from '../shared/format'
    import { projectName } from './project-name'
    import { pageLink } from '../shared/page-links'
    let {
        api,
        pageUrl,
        projects,
        accounts,
        openAccounts,
        refresh,
        notify,
    }: {
        api: ReleaseHubAdminApi
        pageUrl: string
        projects: Project[]
        accounts: AccountView[]
        openAccounts: () => void
        refresh: () => Promise<void>
        notify: (message: string, error?: boolean) => void
    } = $props()
    let editing = $state(false),
        name = $state(''),
        busy = $state(false),
        error = $state(''),
        pendingSync = $state('')
    let keyword = $state(''),
        platform = $state('all'),
        statusFilter = $state('all')
    let spec = $state<ReleaseProjectSpec>({})
    let currentProject = $state<Project>()
    const matchingAccounts = $derived(
        accounts.filter(
            (a) =>
                a.provider === spec.provider &&
                (spec.provider !== 'GITLAB' ||
                    !spec.repositoryUrl ||
                    spec.repositoryUrl.startsWith(a.instanceUrl + '/')),
        ),
    )
    const providers: Record<string, string> = {
        GITHUB: 'GitHub',
        GITEE: 'Gitee',
        GITLAB: 'GitLab',
        CNB: 'CNB',
    }
    const visible = $derived(
        projects.filter(
            (p) =>
                (platform === 'all' || p.spec?.provider === platform) &&
                (statusFilter === 'all' || !!p.spec?.enabled === (statusFilter === 'enabled')) &&
                `${projectName(p)} ${p.spec?.displayName} ${p.spec?.repositoryUrl}`
                    .toLowerCase()
                    .includes(keyword.toLowerCase().trim()),
        ),
    )
    function edit(project?: Project) {
        currentProject = project
        name = project?.metadata.name || ''
        error = ''
        spec = {
            displayName: '',
            autoDetectName: true,
            description: '',
            provider: 'GITHUB',
            repositoryUrl: '',
            syncMinutes: 60,
            enabled: true,
            sortOrder: 0,
            accountName: '',
            ...project?.spec,
        }
        editing = true
    }
    function close() {
        if (!busy) {
            editing = false
        }
    }
    async function save(e: SubmitEvent) {
        e.preventDefault()
        busy = true
        error = ''
        try {
            await api.saveReleaseProject({
                input: {
                    name,
                    spec,
                    credentialSecret: spec.accountName ? undefined : spec.credentialSecret || '',
                },
            })
            editing = false
            await refresh()
            notify('仓库已保存')
        } catch (e) {
            error = errorMessage(e)
        } finally {
            busy = false
        }
    }
    async function sync(project: Project) {
        pendingSync = project.metadata.name
        try {
            await api.syncReleaseProject({ name: project.metadata.name })
            notify('已加入同步队列，可在同步日志中查看结果')
            await refresh()
        } catch (e) {
            notify(errorMessage(e), true)
        } finally {
            pendingSync = ''
        }
    }
    function syncing(p: Project) {
        return (
            pendingSync === p.metadata.name ||
            !!(p.status?.leaseUntil && new Date(p.status.leaseUntil).getTime() > Date.now())
        )
    }
</script>

<div class="rh-panel-heading">
    <div>
        <h1>仓库管理 <span class="rh-heading-count">{projects.length}</span></h1>
        <p>连接代码仓库，自动同步版本与更新日志。</p>
    </div>
    <div class="rh-header-actions">
        <button class="rh-secondary" onclick={() => edit()}>手动添加</button><button
            class="rh-primary"
            onclick={openAccounts}><Icon name="plus" size={16} />从账号添加</button
        >
    </div>
</div>
{#if !projects.length}
    <div class="rh-table-frame">
        <EmptyState
            icon="repository"
            title="连接你的第一个仓库"
            description="支持 GitHub、Gitee、GitLab 和 CNB。历史版本会在首次同步时导入。"
            ><button class="rh-primary" onclick={openAccounts}
                ><Icon name="plus" size={15} />连接账号并选择仓库</button
            ></EmptyState
        >
    </div>
{:else}
    <div class="rh-table-frame">
        <div class="rh-table-toolbar">
            <span class="rh-table-count">代码仓库 <strong>{visible.length}</strong></span>
            <div class="rh-table-filters">
                <label class="rh-console-search"
                    ><Icon name="search" size={16} /><input
                        type="search"
                        aria-label="搜索仓库"
                        bind:value={keyword}
                        placeholder="搜索名称或仓库地址"
                    /></label
                ><select aria-label="筛选代码平台" bind:value={platform}
                    ><option value="all">全部平台</option
                    >{#each Object.entries(providers) as [id, label]}<option value={id}
                            >{label}</option
                        >{/each}</select
                ><select aria-label="筛选仓库状态" bind:value={statusFilter}
                    ><option value="all">全部状态</option><option value="enabled">已启用</option
                    ><option value="disabled">已停用</option></select
                >
            </div>
        </div>
        <div class="rh-table-scroll">
            <table class="rh-table">
                <thead
                    ><tr
                        ><th>项目与仓库</th><th>平台</th><th>状态</th><th>同步频率</th><th
                            >最近同步</th
                        ><th class="rh-align-right">操作</th></tr
                    ></thead
                ><tbody
                    >{#each visible as p}<tr>
                            <td
                                ><div class="rh-project-cell">
                                    <span class="rh-repository-icon"
                                        ><Icon name="repository" size={20} /></span
                                    >
                                    <div>
                                        <strong>{projectName(p)}</strong><a
                                            class="rh-table-subtitle"
                                            href={safeLink(p.spec?.repositoryUrl)}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            title={p.spec?.repositoryUrl}
                                            >{p.spec?.repositoryUrl?.replace(/^https?:\/\//, '')}</a
                                        >
                                    </div>
                                </div></td
                            >
                            <td
                                ><span class="rh-platform"
                                    ><Icon name="code" size={13} />{providers[
                                        p.spec?.provider || ''
                                    ] || p.spec?.provider}</span
                                ></td
                            >
                            <td><StatusBadge state={p.spec?.enabled ? 'ACTIVE' : 'DISABLED'} /></td>
                            <td
                                ><span class="rh-sync-interval"
                                    >{p.spec?.syncMinutes}<small>分钟</small></span
                                ></td
                            >
                            <td
                                ><span class="rh-date">{date(p.status?.lastSync)}</span><span
                                    class="rh-table-subtitle"
                                    class:rh-text-error={(p.status?.failures || 0) > 0}
                                    >{syncing(p)
                                        ? '正在获取版本…'
                                        : p.status?.message || '等待首次同步'}</span
                                ></td
                            >
                            <td
                                ><div class="rh-row-actions">
                                    <button
                                        class="rh-row-button"
                                        disabled={!p.spec?.enabled || syncing(p)}
                                        onclick={() => sync(p)}
                                        ><span class:rh-spinning={syncing(p)}
                                            ><Icon name="refresh" size={14} /></span
                                        >{syncing(p) ? '同步中' : '同步'}</button
                                    ><button
                                        class="rh-icon-button"
                                        aria-label={`编辑 ${projectName(p)}`}
                                        title="编辑仓库"
                                        onclick={() => edit(p)}
                                        ><Icon name="edit" size={15} /></button
                                    >{#if pageUrl}<a
                                            class="rh-icon-button"
                                            href={pageLink(
                                                { 'rh-project': p.metadata.name },
                                                pageUrl,
                                            )}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            aria-label={`查看 ${projectName(p)} 发布页`}
                                            title="查看发布页"><Icon name="arrow" size={15} /></a
                                        >{/if}
                                </div></td
                            >
                        </tr>{/each}</tbody
                >
            </table>
        </div>
        {#if !visible.length}<EmptyState
                icon="search"
                title="没有找到匹配的仓库"
                description="试试其他关键词，或调整平台与状态筛选。"
                ><button
                    class="rh-secondary"
                    onclick={() => {
                        keyword = ''
                        platform = 'all'
                        statusFilter = 'all'
                    }}>清除筛选</button
                ></EmptyState
            >{/if}
        <div class="rh-table-foot">
            <span>共 {projects.length} 个仓库</span><span
                ><Icon name="shield" size={13} />访问令牌安全保存在服务器</span
            >
        </div>
    </div>
{/if}
<div class="rh-page-note">
    <Icon name="info" size={15} /><span
        >首次同步仅导入历史版本；后续发现新版本时，将自动通知已订阅的用户。</span
    >
</div>

{#if editing}<Modal
        title={name ? '编辑仓库' : '连接代码仓库'}
        description={name
            ? '调整项目信息、同步设置与访问令牌。'
            : '连接后即可在发布页展示版本，并向订阅者发送更新。'}
        {close}
        size="wide"
        closeDisabled={busy}
    >
        <form id="rh-project-form" onsubmit={save}>
            <div class="rh-form-section">
                <h3>项目信息</h3>
                <label class="rh-toggle-row">
                    <span
                        ><strong>自动识别 Halo 名称</strong><small
                            >同步时读取插件或主题配置中的名称，每天更新一次。</small
                        ></span
                    >
                    <input type="checkbox" bind:checked={spec.autoDetectName} role="switch" />
                </label>
                {#if spec.autoDetectName && currentProject?.status?.haloDisplayName}
                    <p class="rh-muted" role="status">
                        已识别 {currentProject.status.haloKind === 'Theme'
                            ? '主题'
                            : '插件'}：{currentProject.status.haloDisplayName}
                    </p>
                {:else if spec.autoDetectName && currentProject?.status?.haloNameMessage}
                    <p class="rh-muted" role="status">{currentProject.status.haloNameMessage}</p>
                {/if}
                <div class="rh-form-grid">
                    <label class="rh-field"
                        >{spec.autoDetectName ? '备用名称' : '项目名称'}<input
                            bind:value={spec.displayName}
                            required
                            maxlength="100"
                            placeholder="例如：Halo"
                        /></label
                    ><label class="rh-field"
                        >代码平台<select
                            bind:value={spec.provider}
                            disabled={!!name}
                            onchange={() => (spec.accountName = '')}
                            >{#each Object.entries(providers) as [id, label]}<option value={id}
                                    >{label}</option
                                >{/each}</select
                        ></label
                    >
                </div>
                <label class="rh-field"
                    >仓库地址<input
                        type="url"
                        bind:value={spec.repositoryUrl}
                        readonly={!!name}
                        required
                        placeholder="https://github.com/halo-dev/halo"
                    />{#if name}<small>已有仓库的平台与地址保持固定，更换仓库请添加新项目。</small
                        >{/if}</label
                ><label class="rh-field"
                    >项目描述 <span class="rh-optional">可选</span><textarea
                        bind:value={spec.description}
                        maxlength="1000"
                        rows="2"
                        placeholder="介绍这个项目，将显示在项目发布页。"></textarea></label
                >
            </div>
            <div class="rh-form-section">
                <h3>访问与同步</h3>
                <label class="rh-field"
                    >仓库账号<select bind:value={spec.accountName}>
                        <option value="">独立令牌 / 公开访问</option
                        >{#each matchingAccounts as account}<option value={account.name}
                                >{account.displayName}（{account.username}）</option
                            >{/each}
                    </select><small>选择账号可共用 Token，也可保留原有仓库令牌或公开访问。</small
                    ></label
                >
                {#if !spec.accountName}<SecretField
                        value={spec.credentialSecret || ''}
                        change={(value) => (spec.credentialSecret = value)}
                        help="选择包含 token 字段的 Secret。公开仓库可留空，清除选择后使用匿名访问。"
                        disabled={busy}
                    />{/if}
                <div class="rh-form-grid">
                    <label class="rh-field"
                        >同步间隔 <span class="rh-optional">分钟</span><input
                            type="number"
                            min="5"
                            max="10080"
                            bind:value={spec.syncMinutes}
                            required
                        /></label
                    ><label class="rh-field"
                        >显示排序<input type="number" bind:value={spec.sortOrder} required /><small
                            >数字越小，项目越靠前。</small
                        ></label
                    >
                </div>
                <label class="rh-toggle-row"
                    ><span
                        ><strong>启用项目</strong><small>同步版本，并在前台发布页展示。</small
                        ></span
                    ><input type="checkbox" bind:checked={spec.enabled} role="switch" /></label
                >
            </div>
            {#if error}<p class="rh-error" role="alert">
                    <Icon name="info" size={16} />{error}
                </p>{/if}
        </form>
        {#snippet footer()}<button
                type="button"
                class="rh-secondary"
                disabled={busy}
                onclick={close}>取消</button
            ><button class="rh-primary" type="submit" form="rh-project-form" disabled={busy}
                >{#if busy}<span class="rh-spinner"></span>{/if}{busy
                    ? '正在保存…'
                    : '保存仓库'}</button
            >{/snippet}
    </Modal>{/if}
