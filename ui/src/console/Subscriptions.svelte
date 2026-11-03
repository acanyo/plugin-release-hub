<script lang="ts">
    import { onMount } from 'svelte'
    import type { ReleaseHubAdminApi } from '../api/generated/api/release-hub-admin-api'
    import type { WebhookInput } from '../api/generated/models'
    import type { Subscription, Project } from '../api/client'
    import Modal from '../shared/Modal.svelte'
    import StatusBadge from '../shared/StatusBadge.svelte'
    import EmptyState from '../shared/EmptyState.svelte'
    import Icon from '../shared/Icon.svelte'
    import { date, errorMessage } from '../shared/format'
    import { projectName } from './project-name'
    let {
        api,
        projects,
        notify,
    }: {
        api: ReleaseHubAdminApi
        projects: Project[]
        notify: (message: string, error?: boolean) => void
    } = $props()
    let items = $state<Subscription[]>([]),
        page = $state(1),
        total = $state(0),
        loading = $state(false)
    let editing = $state(false),
        busy = $state(false),
        error = $state(''),
        form = $state<WebhookInput>({})
    let deleting = $state<Subscription | undefined>()
    async function load() {
        loading = true
        try {
            const { data } = await api.listReleaseSubscriptions({ page })
            items = data.items
            total = data.total
        } catch (e) {
            notify(errorMessage(e), true)
        } finally {
            loading = false
        }
    }
    onMount(() => {
        void load()
    })
    function edit(sub?: Subscription) {
        error = ''
        form = {
            name: sub?.metadata.name,
            url: sub?.spec?.webhookUrl || '',
            secret: '',
            projects: [...(sub?.spec?.projects || [])],
            includePrerelease: sub?.spec?.includePrerelease || false,
            enabled: sub ? sub.spec?.state === 'ACTIVE' : true,
        }
        editing = true
    }
    async function save(e: SubmitEvent) {
        e.preventDefault()
        busy = true
        error = ''
        try {
            await api.saveReleaseWebhook({ webhookInput: form })
            form.secret = ''
            editing = false
            await load()
            notify('Webhook 已保存')
        } catch (e) {
            error = errorMessage(e)
        } finally {
            busy = false
        }
    }
    async function action(sub: Subscription, kind: string) {
        try {
            if (kind === 'test') {
                await api.testReleaseNotification({ name: sub.metadata.name })
                notify('测试通知已加入发送队列')
            }
            if (kind === 'toggle') {
                await api.setReleaseSubscriptionState({
                    name: sub.metadata.name,
                    stateInput: { state: sub.spec?.state === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' },
                })
                notify('订阅状态已更新')
            }
            if (kind === 'delete') {
                await api.deleteReleaseSubscription({ name: sub.metadata.name })
                deleting = undefined
                notify('订阅已删除')
            }
            await load()
        } catch (e) {
            notify(errorMessage(e), true)
        }
    }
</script>

<div class="rh-panel-heading">
    <div>
        <h1>订阅管理</h1>
        <p>管理邮件订阅与 Webhook，让版本更新及时送达。</p>
    </div>
    <button class="rh-primary" onclick={() => edit()}
        ><Icon name="plus" size={16} />添加 Webhook</button
    >
</div>
<div class="rh-table-frame">
    <div class="rh-table-toolbar">
        <span class="rh-table-count">全部订阅 <strong>{total}</strong></span><button
            class="rh-quiet-button"
            onclick={load}
            disabled={loading}><Icon name="refresh" size={14} />刷新列表</button
        >
    </div>
    <div class="rh-table-scroll">
        <table class="rh-table">
            <thead
                ><tr
                    ><th>订阅者 / 回调地址</th><th>渠道</th><th>订阅范围</th><th>状态</th><th
                        >操作</th
                    ></tr
                ></thead
            ><tbody
                >{#each items as sub}<tr
                        ><td
                            ><span class="rh-break">{sub.spec?.email || sub.spec?.webhookUrl}</span
                            ><small class="rh-table-subtitle">{date(sub.spec?.createdAt)}</small
                            ></td
                        ><td>{sub.spec?.channel === 'EMAIL' ? '邮件' : 'Webhook'}</td><td
                            >{sub.spec?.projects?.length
                                ? sub.spec.projects
                                      .map(
                                          (id) =>
                                              projects.find((p) => p.metadata.name === id)?.spec
                                                  ?.displayName || id,
                                      )
                                      .join('、')
                                : '全部项目'}<small class="rh-table-subtitle"
                                >{sub.spec?.includePrerelease ? '包含预发布' : '仅正式版'}</small
                            ></td
                        ><td><StatusBadge state={sub.spec?.state} /></td><td
                            ><div class="rh-row-actions">
                                {#if sub.spec?.channel === 'WEBHOOK'}<button
                                        class="rh-row-button"
                                        onclick={() => edit(sub)}>编辑</button
                                    >{/if}{#if sub.spec?.verifiedAt}<button
                                        class="rh-row-button"
                                        onclick={() => action(sub, 'toggle')}
                                        >{sub.spec?.state === 'ACTIVE' ? '停用' : '启用'}</button
                                    >{/if}{#if sub.spec?.state === 'ACTIVE'}<button
                                        class="rh-row-button"
                                        onclick={() => action(sub, 'test')}>测试</button
                                    >{/if}<button
                                    class="rh-row-button rh-text-error"
                                    onclick={() => (deleting = sub)}>删除</button
                                >
                            </div></td
                        ></tr
                    >{/each}</tbody
            >
        </table>
    </div>
    {#if !items.length}
        {#if loading}<div class="rh-loading"><span class="rh-spinner"></span>正在加载订阅…</div>
        {:else}<EmptyState
                icon="users"
                title="还没有订阅"
                description="用户可在发布页订阅邮件，也可以添加 Webhook，将更新推送到你的服务。"
                ><button class="rh-secondary" onclick={() => edit()}
                    ><Icon name="plus" size={15} />添加 Webhook</button
                ></EmptyState
            >{/if}
    {/if}
    <nav class="rh-pagination">
        <button
            class="rh-secondary"
            disabled={page <= 1 || loading}
            onclick={() => {
                page--
                void load()
            }}>上一页</button
        ><span>{total} 条订阅 · 第 {page} 页</span><button
            class="rh-secondary"
            disabled={page * 20 >= total || loading}
            onclick={() => {
                page++
                void load()
            }}>下一页</button
        >
    </nav>
</div>
<div class="rh-page-note">
    <Icon name="shield" size={15} /><span
        >邮箱需经本人验证后才接收通知。订阅者信息仅对有管理权限的用户可见。</span
    >
</div>
{#if editing}<Modal
        title={form.name ? '编辑 Webhook' : '添加 Webhook'}
        description="将版本发布事件发送到你的应用或自动化工作流。"
        close={() => {
            editing = false
            form.secret = ''
        }}
        ><form id="rh-webhook-form" onsubmit={save}>
            <label class="rh-field"
                >回调地址<input
                    type="url"
                    bind:value={form.url}
                    required
                    placeholder="https://example.com/hooks/releases"
                /></label
            >
            <label class="rh-field"
                >签名密钥{form.name ? '（留空保留）' : ''}<input
                    type="password"
                    bind:value={form.secret}
                    required={!form.name}
                    minlength="24"
                    autocomplete="new-password"
                    placeholder="至少 24 个字符"
                /></label
            >
            <fieldset class="rh-project-picker">
                <legend>订阅项目（不选则订阅全部项目）</legend>{#each projects as p}<label
                        class="rh-checkbox"
                        ><input
                            type="checkbox"
                            value={p.metadata.name}
                            bind:group={form.projects}
                        />{projectName(p)}</label
                    >{/each}
            </fieldset>
            <label class="rh-toggle-row"
                ><span
                    ><strong>包含预发布版本</strong><small>同时接收测试版与候选版更新。</small
                    ></span
                ><input
                    type="checkbox"
                    role="switch"
                    bind:checked={form.includePrerelease}
                /></label
            >
            <label class="rh-toggle-row"
                ><span
                    ><strong>启用通知</strong><small>新版本同步成功后向此地址发送事件。</small
                    ></span
                ><input type="checkbox" role="switch" bind:checked={form.enabled} /></label
            >
            <div class="rh-inline-note" style="margin-top:18px">
                <Icon name="shield" size={15} /><span
                    >回调需为公网 HTTPS 地址。事件携带签名与编号，失败时自动重试。</span
                >
            </div>
            {#if error}<p class="rh-error" role="alert">{error}</p>{/if}
        </form>
        {#snippet footer()}<button
                type="button"
                class="rh-secondary"
                disabled={busy}
                onclick={() => {
                    editing = false
                    form.secret = ''
                }}>取消</button
            ><button type="submit" form="rh-webhook-form" class="rh-primary" disabled={busy}
                >{busy ? '保存中…' : '保存 Webhook'}</button
            >{/snippet}
    </Modal>{/if}
{#if deleting}<Modal
        title="删除此订阅？"
        description="删除后，将停止向此订阅发送版本更新。"
        close={() => (deleting = undefined)}
    >
        <p class="rh-muted rh-small">{deleting.spec?.email || deleting.spec?.webhookUrl}</p>
        {#snippet footer()}<button class="rh-secondary" onclick={() => (deleting = undefined)}
                >取消</button
            ><button
                class="rh-primary rh-danger-button"
                onclick={() => deleting && action(deleting, 'delete')}>删除订阅</button
            >{/snippet}
    </Modal>{/if}
