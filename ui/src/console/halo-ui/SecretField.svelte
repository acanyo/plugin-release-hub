<script lang="ts">
    import { getContext, onMount } from 'svelte'
    import { HALO_UI, type HaloSecretOptions, type HaloUi } from './types'

    let {
        value,
        change,
        label = '访问 Token',
        help = '选择或创建包含 token 字段的 Secret。',
        disabled = false,
    }: {
        value: string
        change: (value: string) => void
        label?: string
        help?: string
        disabled?: boolean
    } = $props()
    const halo = getContext<HaloUi>(HALO_UI)
    let target: HTMLDivElement
    let mounted: ReturnType<HaloUi['secret']> | undefined
    const options = $derived<HaloSecretOptions>({ value, change, label, help, disabled })
    onMount(() => {
        mounted = halo.secret(target, options)
        return () => mounted?.destroy()
    })
    $effect(() => {
        const current = options
        mounted?.update(current)
    })
</script>

<div class="rh-halo-secret-field" bind:this={target!}></div>
