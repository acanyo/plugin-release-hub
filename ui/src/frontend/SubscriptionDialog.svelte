<script lang="ts">
    import { untrack } from 'svelte'
    import Modal from '../shared/Modal.svelte'
    import Icon from '../shared/Icon.svelte'
    import ProjectSelect from './components/ProjectSelect.svelte'
    import { publicClient, type ProjectView } from '../api/client'
    import { errorMessage } from '../shared/format'
    let {
        projects,
        currentProject,
        emailEnabled,
        close,
    }: {
        projects: ProjectView[]
        currentProject: string
        emailEnabled: boolean
        close: () => void
    } = $props()
    let method = $state('rss'),
        email = $state(''),
        project = $state(untrack(() => currentProject)),
        pre = $state(false)
    let busy = $state(false),
        message = $state(''),
        success = $state(false),
        copied = $state(false)
    const feed = $derived(
        `${location.origin}/releases/${method === 'atom' ? 'atom' : 'rss'}.xml?project=${encodeURIComponent(project)}&prerelease=${pre}`,
    )
    async function submit(e: SubmitEvent) {
        e.preventDefault()
        busy = true
        message = ''
        try {
            await publicClient().subscribeReleaseEmail({
                emailInput: { email, projects: project ? [project] : [], includePrerelease: pre },
            })
            success = true
        } catch (error) {
            message = errorMessage(error)
        } finally {
            busy = false
        }
    }
    async function copy() {
        try {
            await navigator.clipboard.writeText(feed)
            copied = true
        } catch {
            message = '请选中地址手动复制。'
        }
    }
</script>

<Modal title="不错过下一次更新" {close}>
    <p class="rh-muted">选择你关心的项目，用喜欢的方式获取新版本。</p>
    {#if success}
        <div class="rh-success">
            <Icon name="check" size={28} />
            <h3>请查收确认邮件</h3>
            <p>点击邮件中的链接确认订阅后，即可收到版本更新。未收到时，请检查垃圾邮件。</p>
            <button class="rh-primary" onclick={close}>完成</button>
        </div>
    {:else}
        <div class="rh-methods" aria-label="订阅方式">
            <button
                class:active={method === 'rss'}
                aria-pressed={method === 'rss'}
                onclick={() => {
                    method = 'rss'
                    copied = false
                }}><Icon name="rss" /> RSS</button
            >
            <button
                class:active={method === 'atom'}
                aria-pressed={method === 'atom'}
                onclick={() => {
                    method = 'atom'
                    copied = false
                }}><Icon name="rss" /> Atom</button
            >
            {#if emailEnabled}<button
                    class:active={method === 'email'}
                    aria-pressed={method === 'email'}
                    onclick={() => (method = 'email')}><Icon name="mail" /> 邮件</button
                >{/if}
        </div>
        <div class="rh-field">
            <span>订阅项目</span>
            <ProjectSelect
                {projects}
                value={project}
                change={(next) => (project = next)}
                label="订阅项目"
                fullWidth
            />
        </div>
        <label class="rh-checkbox"
            ><input type="checkbox" bind:checked={pre} />同时接收预发布版本</label
        >
        {#key method}<div class="rh-subscription-panel">
                {#if method === 'email'}
                    <form onsubmit={submit}>
                        <label class="rh-field"
                            >邮箱地址<input
                                type="email"
                                bind:value={email}
                                placeholder="you@example.com"
                                required
                                maxlength="254"
                                autocomplete="email"
                            /></label
                        >
                        <p class="rh-small rh-muted">
                            仅用于版本通知，随时可以通过邮件中的安全链接退订。
                        </p>
                        <button class="rh-primary rh-wide" disabled={busy}
                            >{busy ? '正在提交…' : '发送确认邮件'}<Icon name="arrow" /></button
                        >
                    </form>
                {:else}
                    <label class="rh-field"
                        >订阅地址<input readonly value={feed} aria-label="Feed 订阅地址" /></label
                    >
                    <p class="rh-small rh-muted">将此地址添加到你常用的阅读器，无需注册或登录。</p>
                    <button class="rh-primary rh-wide" onclick={copy}
                        ><Icon name={copied ? 'check' : 'link'} />{copied
                            ? '已复制订阅地址'
                            : '复制订阅地址'}</button
                    >
                {/if}
            </div>{/key}
        {#if message}<p role="alert" class="rh-error">{message}</p>{/if}
    {/if}
</Modal>
