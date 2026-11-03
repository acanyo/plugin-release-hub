<script lang="ts">
    let { state = '', label }: { state?: string; label?: string } = $props()
    const labels: Record<string, string> = {
        ACTIVE: '已启用',
        DISABLED: '已停用',
        PENDING: '待验证',
        UNSUBSCRIBED: '已退订',
        QUEUED: '等待发送',
        SENDING: '发送中',
        SENT: '已送达',
        RETRY: '等待重试',
        FAILED: '失败',
        CANCELLED: '已取消',
        RUNNING: '同步中',
        SUCCEEDED: '成功',
    }
    const tone = $derived(
        ['ACTIVE', 'SENT', 'SUCCEEDED'].includes(state)
            ? 'success'
            : state === 'FAILED'
              ? 'danger'
              : ['PENDING', 'QUEUED', 'SENDING', 'RETRY', 'RUNNING'].includes(state)
                ? 'pending'
                : 'neutral',
    )
</script>

<span class="rh-status" data-tone={tone}><span></span>{label || labels[state] || state}</span>
