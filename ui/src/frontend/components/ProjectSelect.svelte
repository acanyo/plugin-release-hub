<script lang="ts">
    import { onMount } from 'svelte'
    import { Select } from 'bits-ui'
    import type { ProjectView } from '../../api/client'
    import Icon from '../../shared/Icon.svelte'

    let {
        projects,
        value,
        change,
        label = '选择项目',
        fullWidth = false,
    }: {
        projects: ProjectView[]
        value: string
        change: (value: string) => void
        label?: string
        fullWidth?: boolean
    } = $props()

    // '*' cannot collide with a Halo resource name; empty means no Bits UI selection.
    const allProjects = '*'
    const items = $derived([
        { value: allProjects, label: '全部项目' },
        ...projects.flatMap((project) =>
            project.name ? [{ value: project.name, label: project.displayName || project.name }] : [],
        ),
    ])
    const selectedLabel = $derived(
        items.find((item) => item.value === (value || allProjects))?.label || '全部项目',
    )
    let trigger = $state<HTMLButtonElement | null>(null)
    let portalTarget = $state<HTMLElement>()

    function select(next: string) {
        const project = next === allProjects ? '' : next
        if (project !== value) change(project)
    }
    onMount(() => {
        // Keep theme variables and native dialog's top layer when rendering the menu.
        portalTarget = trigger?.closest<HTMLElement>('dialog, .rh-public') || undefined
    })
</script>

<Select.Root
    type="single"
    {items}
    bind:value={() => value || allProjects, select}
    allowDeselect={false}
>
    <Select.Trigger
        class="rh-project-trigger"
        data-full-width={fullWidth}
        aria-label={label}
        bind:ref={trigger}
    >
        <Icon name="repository" size={16} />
        <span class="rh-project-value" title={selectedLabel}>{selectedLabel}</span>
        <span class="rh-project-chevron"><Icon name="chevron" size={13} /></span>
    </Select.Trigger>
    <Select.Portal to={portalTarget} disabled={!portalTarget}>
        <Select.Content
            class="rh-project-menu"
            aria-label={label}
            align="start"
            sideOffset={6}
            collisionPadding={12}
            strategy="fixed"
        >
            <Select.Group>
                <Select.GroupHeading class="rh-project-menu-heading">选择项目</Select.GroupHeading>
                {#each items as item (item.value)}
                    <Select.Item class="rh-project-option" value={item.value} label={item.label}>
                        {#snippet children({ selected })}
                            <Icon
                                name={item.value === allProjects ? 'grid' : 'repository'}
                                size={15}
                            />
                            <span class="rh-project-option-label" title={item.label}
                                >{item.label}</span
                            >
                            {#if selected}<span class="rh-project-check"
                                    ><Icon name="check" size={16} /></span
                                >{/if}
                        {/snippet}
                    </Select.Item>
                {/each}
            </Select.Group>
        </Select.Content>
    </Select.Portal>
</Select.Root>
