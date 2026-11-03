<script lang="ts">
    let { value, change }: { value: string; change: (value: string) => void } = $props()
    const options = [
        { value: 'timeline', label: '更新日志' },
        { value: 'list', label: '所有版本' },
    ]
    function key(event: KeyboardEvent, current: number) {
        const next = {
            ArrowRight: (current + 1) % options.length,
            ArrowLeft: (current - 1 + options.length) % options.length,
            Home: 0,
            End: options.length - 1,
        }[event.key]
        if (next === undefined) return
        event.preventDefault()
        change(options[next]!.value)
        const group = (event.currentTarget as HTMLElement).parentElement
        group?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus()
    }
</script>

<div class="rh-view-navigation">
    <div
        class="rh-view-tabs"
        role="tablist"
        aria-label="版本视图"
        style={`--view-index:${value === 'list' ? 1 : 0}`}
    >
        <span class="rh-view-indicator" aria-hidden="true"></span>
        {#each options as option, i}
            <button
                type="button"
                role="tab"
                id={`rh-view-${option.value}`}
                aria-selected={value === option.value}
                aria-controls="rh-release-view"
                tabindex={value === option.value ? 0 : -1}
                onclick={() => change(option.value)}
                onkeydown={(event) => key(event, i)}>{option.label}</button
            >
        {/each}
    </div>
</div>
