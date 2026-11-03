<script lang="ts">
    import type { ReleaseView } from '../../api/client'
    import Icon from '../../shared/Icon.svelte'
    import { date } from '../../shared/format'
    import { reveal } from '../motion/reveal'
    let { releases }: { releases: ReleaseView[] } = $props()
</script>

<div class="rh-version-table" use:reveal={{ distance: 10 }}>
    <table>
        <thead
            ><tr
                ><th>版本</th><th>所属项目</th><th>发布时间</th><th
                    ><span class="rh-sr-only">查看详情</span></th
                ></tr
            ></thead
        ><tbody
            >{#each releases as release, i}<tr style={`--row-index:${i}`}
                    ><td
                        ><a class="rh-table-version" href={`/releases/versions/${release.name}`}
                            >{release.tag}<span class="rh-badge" class:rh-pre={release.prerelease}
                                >{release.prerelease ? '预发布' : '正式版'}</span
                            ></a
                        ></td
                    ><td><span class="rh-table-project">{release.projectName}</span></td><td
                        ><time datetime={release.publishedAt}
                            >{date(release.publishedAt, true)}</time
                        ></td
                    ><td
                        ><a
                            class="rh-icon-button"
                            href={`/releases/versions/${release.name}`}
                            aria-label={`查看 ${release.tag} 更新日志`}
                            ><Icon name="chevron" size={16} /></a
                        ></td
                    ></tr
                >{/each}</tbody
        >
    </table>
</div>
