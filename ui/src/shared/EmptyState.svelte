<script lang="ts">
    import type { Snippet } from 'svelte'
    import Icon from './Icon.svelte'
    let {
        icon = 'box',
        title,
        description,
        children,
    }: { icon?: string; title: string; description: string; children?: Snippet } = $props()
</script>

<div class="rh-empty">
    <span class="rh-empty-icon"><Icon name={icon} size={24} /></span>
    <h2>{title}</h2>
    <p>{description}</p>
    {#if children}<div class="rh-empty-actions">{@render children()}</div>{/if}
</div>
