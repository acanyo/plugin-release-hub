<script lang="ts">
    let {
        label,
        options,
        value,
        change,
    }: {
        label: string
        options: { value: string; label: string }[]
        value: string
        change: (value: string) => void
    } = $props()
    const index = $derived(
        Math.max(
            0,
            options.findIndex((option) => option.value === value),
        ),
    )
    function key(event: KeyboardEvent, current: number) {
        const next = {
            ArrowRight: (current + 1) % options.length,
            ArrowLeft: (current - 1 + options.length) % options.length,
            Home: 0,
            End: options.length - 1,
        }[event.key]
        if (next === undefined) return
        event.preventDefault()
        const option = options[next]
        if (!option) return
        change(option.value)
        const group = (event.currentTarget as HTMLElement).parentElement
        group?.querySelectorAll<HTMLButtonElement>('button')[next]?.focus()
    }
</script>

<div
    class="rh-segmented"
    role="group"
    aria-label={label}
    style={`--segment-count:${options.length};--segment-index:${index}`}
>
    <span class="rh-segment-indicator" aria-hidden="true"></span>{#each options as option, i}<button
            type="button"
            aria-pressed={value === option.value}
            onclick={() => change(option.value)}
            onkeydown={(event) => key(event, i)}>{option.label}</button
        >{/each}
</div>
