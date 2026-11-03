<script lang="ts">
    import { publicClient } from '../api/client'
    import { errorMessage } from '../shared/format'
    import Icon from '../shared/Icon.svelte'
    import { pageLink } from '../shared/page-links'
    let { unsubscribe }: { unsubscribe: boolean } = $props()
    const token = new URLSearchParams(location.search).get('rh-token') || ''
    let busy = $state(false),
        complete = $state(false),
        error = $state('')
    async function confirm() {
        busy = true
        error = ''
        try {
            const params = { tokenInput: { token } }
            if (unsubscribe) await publicClient().unsubscribeReleaseEmail(params)
            else await publicClient().verifyReleaseEmail(params)
            complete = true
            history.replaceState(history.state, '', pageLink())
        } catch (e) {
            error = errorMessage(e)
        } finally {
            busy = false
        }
    }
</script>

<div class="rh-confirm">
    <span class="rh-confirm-icon"
        ><Icon name={complete ? 'check' : unsubscribe ? 'mail' : 'bell'} size={30} /></span
    >
    <p class="rh-eyebrow">RELEASE HUB · 版本订阅</p>
    <h1>
        {complete
            ? unsubscribe
                ? '已取消订阅'
                : '订阅成功'
            : unsubscribe
              ? '取消版本订阅'
              : '确认你的版本订阅'}
    </h1>
    <p class="rh-muted">
        {complete
            ? unsubscribe
                ? '此订阅将不再收到更新邮件。你可以随时重新订阅。'
                : '下一次版本更新时，我们会通过邮件通知你。'
            : unsubscribe
              ? '确认后，此订阅将不再收到版本更新邮件。'
              : '确认这是你本人发起的请求，即可开始接收版本更新。'}
    </p>
    {#if !complete}<button class="rh-primary" disabled={busy || !token} onclick={confirm}
            >{busy ? '正在处理…' : unsubscribe ? '确认取消订阅' : '确认订阅'}</button
        >{/if}
    {#if error}<p role="alert" class="rh-error">{error}</p>{/if}
    <a class="rh-text-link" href={pageLink()}>返回版本中心 <Icon name="arrow" size={15} /></a>
</div>
