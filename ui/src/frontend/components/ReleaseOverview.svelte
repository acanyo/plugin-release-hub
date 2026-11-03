<script lang="ts">
    import Icon from '../../shared/Icon.svelte'
    import { reveal } from '../motion/reveal'
    import { spotlight } from '../motion/spotlight'
    let {
        total,
        stable,
        prerelease,
        projectCount,
        loading,
    }: {
        total: number
        stable: number
        prerelease: number
        projectCount: number
        loading: boolean
    } = $props()
</script>

<section class="rh-public-overview" aria-label="版本概览">
    {#each [{ label: '已发布版本', value: total, icon: 'box', tone: 'primary' }, { label: '正式版本', value: stable, icon: 'shield', tone: 'primary' }, { label: '预发布版本', value: prerelease, icon: 'activity', tone: 'secondary' }, { label: '开源项目', value: projectCount, icon: 'repository', tone: 'secondary' }] as metric, i}<div
            class="rh-overview-metric"
            data-tone={metric.tone}
            use:reveal={{ delay: i * 45, distance: 10 }}
            use:spotlight
        >
            <Icon name={metric.icon} size={20} /><strong
                >{loading ? '—' : metric.value.toLocaleString()}</strong
            ><span>{metric.label}</span>
        </div>{/each}
</section>
