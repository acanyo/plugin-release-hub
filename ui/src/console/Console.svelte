<script lang="ts">
    import logoUrl from '../../../src/main/resources/logo.svg'
    import { onMount, setContext, untrack } from 'svelte'
    import { HALO_UI, type HaloUi } from './halo-ui/types'
    import type { AxiosInstance } from 'axios'
    import {
        adminClient,
        publicClient,
        type Project,
        type Stats,
        type AccountView,
    } from '../api/client'
    import PublishingGuide from './PublishingGuide.svelte'
    import { errorMessage } from '../shared/format'
    import Projects from './Projects.svelte'
    import Accounts from './accounts/Accounts.svelte'
    import Subscriptions from './Subscriptions.svelte'
    import Logs from './Logs.svelte'
    import Icon from '../shared/Icon.svelte'
    import '../shared/styles.css'
    import './console.css'

    let { axios, haloUi }: { axios: AxiosInstance; haloUi: HaloUi } = $props()
    setContext(
        HALO_UI,
        untrack(() => haloUi),
    )
    const api = $derived(adminClient(axios))
    const tabs = [
        { id: 'projects', label: '仓库管理', icon: 'repository' },
        { id: 'accounts', label: '仓库账号', icon: 'users' },
        { id: 'subscriptions', label: '订阅管理', icon: 'users' },
        { id: 'sync', label: '同步日志', icon: 'history' },
        { id: 'delivery', label: '发送记录', icon: 'mail' },
    ]
    const initialTab = new URLSearchParams(location.search).get('tab') || 'projects'
    let projects = $state<Project[]>([]),
        pageUrl = $state(''),
        accounts = $state<AccountView[]>([]),
        stats = $state<Stats>({})
    let selected = $state(tabs.some((t) => t.id === initialTab) ? initialTab : 'projects')
    let message = $state(''),
        failed = $state(false),
        loading = $state(true),
        refreshing = $state(false),
        pageError = $state('')
    let timer: ReturnType<typeof setTimeout>

    function notify(text: string, error = false) {
        message = text
        failed = error
        clearTimeout(timer)
        timer = setTimeout(() => (message = ''), 6000)
    }
    function selectTab(id: string) {
        selected = id
        const url = new URL(location.href)
        url.searchParams.set('tab', id)
        history.replaceState(history.state, '', url)
    }
    function tabKey(event: KeyboardEvent, index: number) {
        const next = {
            ArrowRight: (index + 1) % tabs.length,
            ArrowLeft: (index - 1 + tabs.length) % tabs.length,
            Home: 0,
            End: tabs.length - 1,
        }[event.key]
        if (next === undefined) return
        event.preventDefault()
        const tab = tabs[next]
        if (!tab) return
        selectTab(tab.id)
        const parent = (event.currentTarget as HTMLElement).parentElement
        parent?.querySelector<HTMLButtonElement>(`#rh-admin-tab-${tab.id}`)?.focus()
    }
    async function refresh() {
        if (refreshing) return
        refreshing = true
        try {
            const [p, s, a, publicSettings] = await Promise.all([
                api.listManagedProjects(),
                api.getReleaseHubStats(),
                api.listRepositoryAccounts(),
                publicClient().getReleaseHubSettings(),
            ])
            projects = p.data
            stats = s.data
            accounts = a.data
            pageUrl = publicSettings.data.pageUrl || ''
            pageError = ''
        } catch (e) {
            pageError = errorMessage(e)
        } finally {
            loading = false
            refreshing = false
        }
    }
    onMount(() => {
        void refresh()
        const poll = setInterval(() => {
            if (!document.hidden) void refresh()
        }, 15000)
        return () => {
            clearInterval(poll)
            clearTimeout(timer)
        }
    })
</script>

<div class="rh-root rh-admin">
    <header class="rh-admin-header">
        <a class="rh-admin-brand" href="/console/release-hub">
            <span class="rh-admin-mark"
                ><img src={logoUrl} width="33" height="33" alt="" aria-hidden="true" /></span
            >
            <strong>版本中心</strong>
        </a>
        <div class="rh-header-actions">
            <a
                class="rh-quiet-button"
                href="https://docs.xhhao.com"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="查看使用文档"
                title="查看使用文档"><Icon name="repository" size={16} /><span>文档</span></a
            >
            <a
                class="rh-quiet-button"
                href="https://qm.qq.com/q/YqlBX8d2Ei"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="加入 Halo 插件主题 QQ 交流群"
                title="加入 QQ 交流群"><Icon name="users" size={16} /><span>QQ 群</span></a
            >
            <a
                class="rh-quiet-button"
                href="/console/plugins/release-hub"
                aria-label="插件设置"
                title="插件设置"><Icon name="settings" size={16} /><span>插件设置</span></a
            >
            {#if pageUrl}<a
                    class="rh-secondary"
                    href={pageUrl}
                    target="_blank"
                    rel="noopener noreferrer">查看发布页 <Icon name="arrow" size={15} /></a
                >
            {:else}<a class="rh-secondary" href="/console/single-pages"
                    >创建发布页面 <Icon name="plus" size={15} /></a
                >{/if}
        </div>
    </header>
    <div class="rh-admin-tabs" role="tablist" aria-label="版本中心管理">
        {#each tabs as tab, index}
            <button
                type="button"
                id={`rh-admin-tab-${tab.id}`}
                role="tab"
                aria-selected={selected === tab.id}
                aria-controls="rh-admin-panel"
                tabindex={selected === tab.id ? 0 : -1}
                class:active={selected === tab.id}
                onclick={() => selectTab(tab.id)}
                onkeydown={(e) => tabKey(e, index)}
                ><Icon name={tab.icon} size={16} />{tab.label}</button
            >
        {/each}
    </div>
    <main class="rh-admin-content">
        <PublishingGuide {pageUrl} />
        <section class="rh-overview" aria-label="版本中心概览">
            <div class="rh-overview-heading">
                <span>工作区概览</span><button
                    class="rh-icon-button"
                    aria-label="刷新概览"
                    title="刷新概览"
                    disabled={refreshing}
                    onclick={refresh}
                    ><span class:rh-spinning={refreshing}><Icon name="refresh" size={15} /></span
                    ></button
                >
            </div>
            <div class="rh-admin-stats">
                {#each [{ label: '代码仓库', value: stats.projects, icon: 'repository', tab: 'projects' }, { label: '已同步版本', value: stats.releases, icon: 'box', tab: 'projects' }, { label: '邮件订阅', value: stats.activeEmails, icon: 'users', tab: 'subscriptions' }, { label: 'Webhook', value: stats.activeWebhooks, icon: 'link', tab: 'subscriptions' }, { label: '发送失败', value: stats.failedDeliveries, icon: 'activity', tab: 'delivery' }] as item}
                    <button
                        class="rh-stat"
                        class:rh-stat-alert={item.label === '发送失败' && !!item.value}
                        onclick={() => selectTab(item.tab)}
                        ><span class="rh-stat-label"
                            ><Icon name={item.icon} size={14} />{item.label}</span
                        ><strong>{loading ? '—' : (item.value || 0).toLocaleString()}</strong
                        ></button
                    >
                {/each}
            </div>
        </section>
        {#if pageError}<div class="rh-error" role="alert">
                <Icon name="info" size={16} /><span>{pageError}</span><button
                    class="rh-text-link"
                    onclick={refresh}>重新加载</button
                >
            </div>{/if}
        <div
            id="rh-admin-panel"
            class="rh-admin-panel"
            role="tabpanel"
            aria-labelledby={`rh-admin-tab-${selected}`}
        >
            {#if loading}<div class="rh-loading">
                    <span class="rh-spinner"></span>正在加载版本中心…
                </div>
            {:else if selected === 'projects'}<Projects
                    {pageUrl}
                    {api}
                    {projects}
                    {accounts}
                    {refresh}
                    {notify}
                    openAccounts={() => selectTab('accounts')}
                />
            {:else if selected === 'accounts'}<Accounts {api} {accounts} {refresh} {notify} />
            {:else if selected === 'subscriptions'}<Subscriptions {api} {projects} {notify} />
            {:else}{#key selected}<Logs
                        {api}
                        {projects}
                        {notify}
                        kind={selected === 'sync' ? 'sync' : 'delivery'}
                    />{/key}{/if}
        </div>
    </main>
    <footer class="rh-admin-footer"><span>版本中心</span><span>版本发布与订阅通知</span></footer>
    {#if message}<div class="rh-toast" data-tone={failed ? 'danger' : 'success'} role="status">
            <Icon name={failed ? 'info' : 'check'} size={17} /><span>{message}</span><button
                class="rh-icon-button"
                aria-label="关闭提示"
                onclick={() => (message = '')}><Icon name="close" size={15} /></button
            >
        </div>{/if}
</div>
