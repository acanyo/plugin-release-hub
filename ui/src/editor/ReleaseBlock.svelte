<script lang="ts">
    import { onMount } from 'svelte'
    import type { Writable } from 'svelte/store'
    import { publicClient, type ProjectView } from '../api/client'
    import { errorMessage } from '../shared/format'
    import type { EmbedOptions } from '../shared/embed-options'
    import logoUrl from '../../../src/main/resources/logo.svg'
    import '../shared/styles.css'
    import './editor.css'

    let {
        config,
        change,
        remove,
    }: {
        config: Writable<EmbedOptions>
        change: (value: Partial<EmbedOptions>) => void
        remove: () => void
    } = $props()
    let projects = $state<ProjectView[]>([]),
        loading = $state(true),
        error = $state('')
    const selected = $derived(projects.find((p) => p.name === $config.project))
    async function load() {
        loading = true
        error = ''
        try {
            projects = (await publicClient().listPublicProjects()).data
        } catch (e) {
            error = errorMessage(e)
        } finally {
            loading = false
        }
    }
    onMount(() => {
        void load()
    })
</script>

<section class="rh-root rh-editor-card" aria-label="版本中心组件设置">
    <header class="rh-editor-heading" data-drag-handle>
        <img src={logoUrl} width="32" height="32" alt="" />
        <div>
            <strong>版本中心</strong>
            <p>发布后自动展示已同步的版本，无需粘贴更新日志。</p>
        </div>
        <button type="button" class="rh-quiet-button" onclick={remove}>移除</button>
    </header>
    <div class="rh-editor-fields">
        <label
            >展示项目
            <select
                aria-label="展示项目"
                value={$config.project}
                onchange={(e) => change({ project: e.currentTarget.value })}
                disabled={loading}
            >
                <option value="">全部已启用项目</option>
                {#if $config.project && !selected}<option value={$config.project}
                        >当前项目不可用</option
                    >{/if}
                {#each projects as project}<option value={project.name}
                        >{project.displayName}</option
                    >{/each}
            </select>
        </label>
        <label
            >首次展示
            <select
                aria-label="首次展示"
                value={$config.view}
                onchange={(e) => change({ view: e.currentTarget.value as EmbedOptions['view'] })}
            >
                <option value="timeline">更新日志时间线</option><option value="list"
                    >所有版本表格</option
                >
            </select>
        </label>
        <label
            >默认版本范围
            <select
                aria-label="默认版本范围"
                value={$config.channel}
                onchange={(e) =>
                    change({ channel: e.currentTarget.value as EmbedOptions['channel'] })}
            >
                <option value="all">正式版和预发布</option><option value="stable">仅正式版</option
                ><option value="prerelease">仅预发布</option>
            </select>
        </label>
        <label class="rh-editor-checkbox"
            ><input
                type="checkbox"
                checked={$config.overview}
                onchange={(e) => change({ overview: e.currentTarget.checked })}
            />显示版本概览</label
        >
    </div>
    {#if error}<p class="rh-editor-note" role="alert">
            项目加载失败：{error} <button type="button" onclick={load}>重新加载</button>
        </p>
    {:else if !loading && !projects.length}<p class="rh-editor-note">
            还没有启用的项目，请先在「版本中心」连接仓库并同步版本。
        </p>
    {:else}<p class="rh-editor-note">
            {loading
                ? '正在读取项目…'
                : `当前展示：${$config.project ? selected?.displayName || '已停用或删除的项目' : '全部已启用项目'}`}。保存并发布页面后即可访问。
        </p>{/if}
</section>
