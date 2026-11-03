<script lang="ts">
    import Icon from '../shared/Icon.svelte'
    let { pageUrl }: { pageUrl: string } = $props()
</script>

<section class="rh-publishing-guide" aria-label="如何发布版本页面">
    <div><Icon name="info" size={18} /><strong>如何发布到你的网站</strong></div>
    <ol>
        <li>在 Halo「页面」中新建一个页面。</li>
        <li>
            在默认编辑器工具箱选择「版本中心」，或输入 <kbd>/版本中心</kbd
            >，选择展示项目、视图和版本范围。
        </li>
        <li>保存并发布页面，按需添加到站点菜单。后续同步到的新版本会自动更新。</li>
        <li>
            将发布后的完整地址填入「插件设置 → 站点与品牌 → 已发布的版本页面地址」，用于
            Feed、邮件详情、确认和退订链接。
        </li>
    </ol>
    <div class="rh-header-actions">
        <a class="rh-text-link" href="/console/single-pages"
            >前往页面管理 <Icon name="arrow" size={14} /></a
        >
        <a class="rh-text-link" href="/console/plugins/release-hub?tab=general"
            >设置发布地址 <Icon name="settings" size={14} /></a
        >
        {#if pageUrl}<a
                class="rh-text-link"
                href={pageUrl}
                target="_blank"
                rel="noopener noreferrer">查看已发布页面 <Icon name="arrow" size={14} /></a
            >
        {:else}<span class="rh-muted">尚未填写发布页面地址</span>{/if}
    </div>
</section>
