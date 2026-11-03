<script lang="ts">
    import { getContext, onMount, type Snippet } from 'svelte'
    import { HALO_UI, type HaloUi } from '../console/halo-ui/types'
    import Icon from './Icon.svelte'
    let {
        title,
        description,
        close,
        children,
        footer,
        size = 'normal',
        closeDisabled = false,
    }: {
        title: string
        description?: string
        close: () => void
        children: Snippet
        footer?: Snippet
        size?: 'normal' | 'wide'
        closeDisabled?: boolean
    } = $props()
    let dialog = $state<HTMLDialogElement>(null!)
    const halo = getContext<HaloUi | undefined>(HALO_UI)
    let host = $state<HTMLDivElement>(null!),
        body = $state<HTMLDivElement>(null!),
        actions = $state<HTMLDivElement>(null!)
    let closing = false
    function animated() {
        return (
            !!dialog.closest('.rh-public') &&
            !matchMedia('(prefers-reduced-motion: reduce)').matches
        )
    }
    async function requestClose() {
        if (closing) return
        closing = true
        if (animated()) {
            await dialog
                .animate(
                    [
                        { opacity: 1, transform: 'translateY(0) scale(1)' },
                        { opacity: 0, transform: 'translateY(8px) scale(.98)' },
                    ],
                    { duration: 150, easing: 'ease-in', fill: 'forwards' },
                )
                .finished.catch(() => {})
        }
        close()
        closing = false
    }
    onMount(() => {
        const prior = document.activeElement as HTMLElement
        if (halo) {
            const mounted = halo.modal(host, {
                title,
                description,
                width: size === 'wide' ? 600 : 480,
                body,
                footer: footer ? actions : undefined,
                close: () => close(),
                closeDisabled: () => closeDisabled,
            })
            return () => {
                mounted.destroy()
                prior?.focus()
            }
        }
        dialog.showModal()
        if (animated())
            dialog.animate(
                [
                    { opacity: 0, transform: 'translateY(18px) scale(.97)' },
                    { opacity: 1, transform: 'translateY(0) scale(1)' },
                ],
                { duration: 260, easing: 'cubic-bezier(.2,.75,.25,1)' },
            )
        return () => {
            dialog.close()
            prior?.focus()
        }
    })
</script>

{#if halo}
    <div bind:this={host!}></div>
    <div hidden>
        <div bind:this={body!}>{@render children()}</div>
        {#if footer}<div bind:this={actions!}>{@render footer()}</div>{/if}
    </div>
{:else}<dialog
        class="rh-dialog"
        data-size={size}
        bind:this={dialog!}
        oncancel={(e) => {
            e.preventDefault()
            void requestClose()
        }}
        aria-label={title}
    >
        <div class="rh-dialog-head">
            <div>
                <h2>{title}</h2>
                {#if description}<p>{description}</p>{/if}
            </div>
            <button
                type="button"
                class="rh-icon-button"
                aria-label="关闭弹窗"
                onclick={requestClose}><Icon name="close" /></button
            >
        </div>
        <div class="rh-dialog-body">{@render children()}</div>
        {#if footer}<div class="rh-dialog-footer">{@render footer()}</div>{/if}
    </dialog>
{/if}
