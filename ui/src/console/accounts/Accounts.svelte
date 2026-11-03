<script lang="ts">
    import type { ReleaseHubAdminApi } from '../../api/generated/api/release-hub-admin-api'
    import type { AccountView } from '../../api/client'
    import { date, errorMessage } from '../../shared/format'
    import Icon from '../../shared/Icon.svelte'
    import Modal from '../../shared/Modal.svelte'
    import EmptyState from '../../shared/EmptyState.svelte'
    import AccountEditor from './AccountEditor.svelte'
    import RepositoryPicker from './RepositoryPicker.svelte'
    import { platforms } from './platforms'

    let {
        api,
        accounts,
        refresh,
        notify,
    }: {
        api: ReleaseHubAdminApi
        accounts: AccountView[]
        refresh: () => Promise<void>
        notify: (message: string, error?: boolean) => void
    } = $props()
    let editing = $state(false),
        current = $state<AccountView>(),
        picking = $state<AccountView>(),
        removing = $state<AccountView>()
    let busy = $state(false),
        error = $state('')
    function edit(account?: AccountView) {
        current = account
        editing = true
    }
    async function saved(account: AccountView) {
        editing = false
        await refresh()
        notify('仓库账号已验证并保存')
        if (!current) picking = account
    }
    async function remove() {
        if (!removing?.name) return
        busy = true
        error = ''
        try {
            await api.removeReleaseHubAccount({ name: removing.name })
            removing = undefined
            await refresh()
            notify('仓库账号已删除')
        } catch (e) {
            error = errorMessage(e)
        } finally {
            busy = false
        }
    }
</script>

<div class="rh-panel-heading">
    <div>
        <h1>仓库账号 <span class="rh-heading-count">{accounts.length}</span></h1>
        <p>连接平台账号，再勾选需要同步的仓库。</p>
    </div>
    <button class="rh-primary" onclick={() => edit()}><Icon name="plus" size={16} />添加账号</button
    >
</div>
<div class="rh-table-frame">
    {#if !accounts.length}<EmptyState
            icon="users"
            title="先连接一个代码平台账号"
            description="填写一次 Token，即可查询并批量添加 GitHub、Gitee、GitLab 或 CNB 仓库。"
            ><button class="rh-primary" onclick={() => edit()}
                ><Icon name="plus" size={15} />添加账号</button
            ></EmptyState
        >
    {:else}<div class="rh-table-scroll">
            <table class="rh-table">
                <thead
                    ><tr
                        ><th>账号</th><th>代码平台</th><th>关联仓库</th><th>最近验证</th><th
                            class="rh-align-right">操作</th
                        ></tr
                    ></thead
                ><tbody>
                    {#each accounts as account (account.name)}<tr
                            ><td
                                ><div class="rh-project-cell">
                                    <span class="rh-repository-icon"
                                        ><Icon name="users" size={20} /></span
                                    >
                                    <div>
                                        <strong>{account.displayName}</strong><span
                                            class="rh-table-subtitle"
                                            >{account.username} · {account.instanceUrl?.replace(
                                                'https://',
                                                '',
                                            )}</span
                                        >
                                    </div>
                                </div></td
                            ><td
                                ><span class="rh-platform">{platforms[account.provider || '']}</span
                                ></td
                            ><td>{account.linkedProjects || 0} 个</td><td
                                ><span class="rh-date">{date(account.verifiedAt)}</span><span
                                    class="rh-table-subtitle">Token 已保存</span
                                ></td
                            ><td
                                ><div class="rh-row-actions">
                                    <button
                                        class="rh-row-button"
                                        onclick={() => (picking = account)}
                                        ><Icon name="repository" size={14} />选择仓库</button
                                    ><button
                                        class="rh-icon-button"
                                        aria-label={`编辑账号 ${account.displayName}`}
                                        title="编辑账号"
                                        onclick={() => edit(account)}
                                        ><Icon name="edit" size={15} /></button
                                    ><button
                                        class="rh-icon-button"
                                        aria-label={`删除账号 ${account.displayName}`}
                                        title={account.linkedProjects
                                            ? '请先解除关联仓库'
                                            : '删除账号'}
                                        disabled={!!account.linkedProjects}
                                        onclick={() => {
                                            removing = account
                                            error = ''
                                        }}><Icon name="close" size={15} /></button
                                    >
                                </div></td
                            ></tr
                        >{/each}
                </tbody>
            </table>
        </div>
        <div class="rh-table-foot">
            <span>共 {accounts.length} 个账号</span><span
                ><Icon name="shield" size={13} />Token 仅保存在服务器</span
            >
        </div>{/if}
</div>
<div class="rh-page-note">
    <Icon name="info" size={15} /><span
        >GitHub 可在<a href="/console/plugins/release-hub">插件设置</a>中配置 API
        代理，其他平台使用直连。</span
    >
</div>
{#if editing}<AccountEditor {api} account={current} close={() => (editing = false)} {saved} />{/if}
{#if picking}<RepositoryPicker
        {api}
        account={picking}
        close={() => (picking = undefined)}
        imported={refresh}
    />{/if}
{#if removing}<Modal
        title="删除仓库账号"
        closeDisabled={busy}
        description={`删除仓库账号「${removing.displayName}」。`}
        close={() => {
            if (!busy) removing = undefined
        }}
        ><p class="rh-muted">仅移除账号记录，选用的 Halo Secret 会保留。</p>
        {#if error}<p class="rh-error" role="alert">{error}</p>{/if}{#snippet footer()}<button
                class="rh-secondary"
                disabled={busy}
                onclick={() => (removing = undefined)}>取消</button
            ><button class="rh-primary" disabled={busy} onclick={remove}
                >{busy ? '正在删除…' : '删除账号'}</button
            >{/snippet}</Modal
    >{/if}
