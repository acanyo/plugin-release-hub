<script lang="ts">
    import type { ReleaseHubAdminApi } from '../../api/generated/api/release-hub-admin-api'
    import type { AccountView } from '../../api/client'
    import { untrack } from 'svelte'
    import { errorMessage } from '../../shared/format'
    import Icon from '../../shared/Icon.svelte'
    import Modal from '../../shared/Modal.svelte'
    import { platforms, tokenHelp } from './platforms'
    import SecretField from '../halo-ui/SecretField.svelte'

    let {
        api,
        account,
        close,
        saved,
    }: {
        api: ReleaseHubAdminApi
        account?: AccountView
        close: () => void
        saved: (account: AccountView) => Promise<void>
    } = $props()
    let displayName = $state(untrack(() => account?.displayName || ''))
    let provider = $state(untrack(() => account?.provider || 'GITHUB'))
    let instanceUrl = $state(untrack(() => account?.instanceUrl || 'https://gitlab.com'))
    let credentialSecret = $state(untrack(() => account?.credentialSecret || '')),
        busy = $state(false),
        error = $state('')
    const help = $derived(tokenHelp[provider])
    function dismiss() {
        if (!busy) {
            close()
        }
    }
    async function submit(event: SubmitEvent) {
        event.preventDefault()
        busy = true
        error = ''
        try {
            const { data } = await api.saveRepositoryAccount({
                accountInput: {
                    name: account?.name,
                    displayName,
                    provider,
                    instanceUrl: provider === 'GITLAB' ? instanceUrl : '',
                    credentialSecret,
                },
            })
            await saved(data)
        } catch (e) {
            error = errorMessage(e)
        } finally {
            busy = false
        }
    }
</script>

<Modal
    title={account ? '编辑仓库账号' : '添加仓库账号'}
    description="保存时会验证 Token，并识别对应的平台账号。"
    close={dismiss}
    size="wide"
    closeDisabled={busy}
>
    <form id="rh-account-form" onsubmit={submit}>
        <div class="rh-form-grid">
            <label class="rh-field"
                >账号名称<input
                    bind:value={displayName}
                    required
                    maxlength="100"
                    placeholder="例如：个人 GitHub、团队 GitLab"
                /></label
            >
            <label class="rh-field"
                >代码平台<select bind:value={provider} disabled={!!account}>
                    {#each Object.entries(platforms) as [value, label]}<option {value}
                            >{label}</option
                        >{/each}
                </select></label
            >
        </div>
        {#if provider === 'GITLAB'}<label class="rh-field"
                >GitLab 站点<input
                    type="url"
                    bind:value={instanceUrl}
                    readonly={!!account}
                    required
                    placeholder="https://gitlab.com"
                /><small>只填写 HTTPS 站点根地址，不包含仓库路径。</small></label
            >{/if}
        <SecretField
            value={credentialSecret}
            change={(value) => (credentialSecret = value)}
            label="访问 Token"
            help="选择或创建包含 token 字段的 Secret。关联仓库共用此密钥，更新密钥后同步使用新 Token。"
            disabled={busy}
        />
        {#if help}<div class="rh-inline-note">
                <Icon name="info" size={16} /><span
                    >{help.text}
                    <a
                        href={help.url}
                        class="rh-text-link"
                        target="_blank"
                        rel="noopener noreferrer">管理 Token <Icon name="arrow" size={12} /></a
                    ></span
                >
            </div>{/if}
        {#if provider === 'GITHUB'}<p class="rh-account-proxy-note">
                无法连接 GitHub 时，可在<a
                    href="/console/plugins/release-hub"
                    target="_blank"
                    rel="noopener noreferrer">插件设置 → GitHub API 代理</a
                >中配置受信任的 API 代理。
            </p>{/if}
        {#if error}<p class="rh-error" role="alert"><Icon name="info" size={16} />{error}</p>{/if}
    </form>
    {#snippet footer()}<button class="rh-secondary" disabled={busy} onclick={dismiss}>取消</button
        ><button class="rh-primary" type="submit" form="rh-account-form" disabled={busy}
            >{#if busy}<span class="rh-spinner"></span>{/if}{busy
                ? '正在验证账号…'
                : '验证并保存'}</button
        >{/snippet}
</Modal>
