<script lang="ts">
    import { onMount } from 'svelte'
    import type { ReleaseHubAdminApi } from '../api/generated/api/release-hub-admin-api'
    import type { SyncLog, Delivery, Project } from '../api/client'
    import Icon from '../shared/Icon.svelte'
    import StatusBadge from '../shared/StatusBadge.svelte'
    import EmptyState from '../shared/EmptyState.svelte'
    import { date, errorMessage } from '../shared/format'
    import { projectName } from './project-name'
    let {
        api,
        kind,
        projects,
        notify,
    }: {
        api: ReleaseHubAdminApi
        kind: 'sync' | 'delivery'
        projects: Project[]
        notify: (message: string, error?: boolean) => void
    } = $props()
    let syncs = $state<SyncLog[]>([]),
        deliveries = $state<Delivery[]>([]),
        page = $state(1),
        total = $state(0),
        loading = $state(false)
    async function load() {
        loading = true
        try {
            if (kind === 'sync') {
                const { data } = await api.listReleaseSyncLogs({ page })
                syncs = data.items
                total = data.total
            } else {
                const { data } = await api.listReleaseDeliveries({ page })
                deliveries = data.items
                total = data.total
            }
        } catch (e) {
            notify(errorMessage(e), true)
        } finally {
            loading = false
        }
    }
    async function retry(name: string) {
        try {
            await api.retryReleaseDelivery({ name })
            notify('已加入重试队列')
            await load()
        } catch (e) {
            notify(errorMessage(e), true)
        }
    }
    onMount(() => {
        void load()
    })
</script>

<div class="rh-panel-heading">
    <div>
        <h1>{kind === 'sync' ? '同步日志' : '发送记录'}</h1>
        <p>
            {kind === 'sync'
                ? '查看各仓库的同步结果、导入数量与执行详情。'
                : '查看通知投递结果，失败任务会自动重试，最多尝试 5 次。'}
        </p>
    </div>
    <button class="rh-secondary" onclick={load} disabled={loading}
        ><Icon name="refresh" size={14} />刷新记录</button
    >
</div>
<div class="rh-table-frame">
    <div class="rh-table-toolbar">
        <span class="rh-table-count"
            >{kind === 'sync' ? '仓库同步记录' : '通知投递记录'} <strong>{total}</strong></span
        ><span class="rh-small rh-muted">按时间从新到旧排列</span>
    </div>
    <div class="rh-table-scroll">
        <table class="rh-table">
            {#if kind === 'sync'}<thead
                    ><tr
                        ><th>项目</th><th>开始时间</th><th>状态</th><th>新增版本</th><th>详情</th
                        ></tr
                    ></thead
                ><tbody
                    >{#each syncs as log}<tr>
                            <td
                                ><div class="rh-project-cell">
                                    <span class="rh-repository-icon"
                                        ><Icon name="repository" size={18} /></span
                                    ><strong
                                        >{projectName(
                                            projects.find(
                                                (p) => p.metadata.name === log.spec?.project,
                                            ),
                                        ) || log.spec?.project}</strong
                                    >
                                </div></td
                            >
                            <td><span class="rh-date">{date(log.spec?.startedAt)}</span></td><td
                                ><StatusBadge state={log.spec?.state} /></td
                            ><td
                                >{log.spec?.imported || 0}{#if log.spec?.initialImport}<small
                                        class="rh-table-subtitle">首次导入</small
                                    >{/if}</td
                            ><td
                                ><span class="rh-log-message"
                                    >{log.spec?.message || '正在同步版本…'}</span
                                ></td
                            >
                        </tr>{/each}</tbody
                >
            {:else}<thead
                    ><tr
                        ><th>通知与订阅</th><th>创建时间</th><th>状态</th><th>尝试次数</th><th
                            >投递详情</th
                        ></tr
                    ></thead
                ><tbody
                    >{#each deliveries as delivery}<tr>
                            <td
                                ><strong
                                    >{delivery.spec?.type === 'VERIFY'
                                        ? '邮箱验证'
                                        : delivery.spec?.type === 'TEST'
                                          ? '测试通知'
                                          : '新版本发布'}</strong
                                ><small
                                    class="rh-table-subtitle"
                                    title={delivery.spec?.subscription}
                                    >{delivery.spec?.subscription}</small
                                ></td
                            >
                            <td><span class="rh-date">{date(delivery.spec?.createdAt)}</span></td
                            ><td><StatusBadge state={delivery.spec?.state} /></td><td
                                >{delivery.spec?.attempts || 0} / 5</td
                            ><td
                                ><span class="rh-log-message"
                                    >{delivery.spec?.message ||
                                        (delivery.spec?.sentAt
                                            ? date(delivery.spec.sentAt)
                                            : '等待处理')}</span
                                >{#if delivery.spec?.state === 'FAILED'}<div>
                                        <button
                                            class="rh-text-link"
                                            onclick={() => retry(delivery.metadata.name)}
                                            >重新发送 <Icon name="refresh" size={13} /></button
                                        >
                                    </div>{/if}{#if delivery.spec?.state === 'RETRY'}<small
                                        class="rh-table-subtitle"
                                        >下次尝试 {date(delivery.spec.nextAttempt)}</small
                                    >{/if}</td
                            >
                        </tr>{/each}</tbody
                >{/if}
        </table>
    </div>
    {#if !total}{#if loading}<div class="rh-loading">
                <span class="rh-spinner"></span>正在加载记录…
            </div>{:else}<EmptyState
                icon={kind === 'sync' ? 'history' : 'mail'}
                title={kind === 'sync' ? '暂无同步记录' : '暂无发送记录'}
                description={kind === 'sync'
                    ? '添加仓库并开始同步，即可查看执行结果与导入数量。'
                    : '邮件验证、版本更新和测试通知的发送结果会出现在这里。'}
            />{/if}{/if}
    <nav class="rh-pagination" aria-label="记录分页">
        <button
            class="rh-secondary"
            disabled={page <= 1 || loading}
            onclick={() => {
                page--
                void load()
            }}>上一页</button
        ><span>共 {total} 条 · 第 {page} 页</span><button
            class="rh-secondary"
            disabled={page * 20 >= total || loading}
            onclick={() => {
                page++
                void load()
            }}>下一页</button
        >
    </nav>
</div>
