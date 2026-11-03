# 版本中心

面向 Halo 的多平台版本发布中心。支持 GitHub、Gitee、GitLab、CNB，提供本地版本时间线、RSS/Atom、经过邮箱确认的邮件订阅，以及签名 Webhook 通知。

[演示站](https://www.xhhao.com) · [使用文档](https://docs.xhhao.com) · [加入 QQ 交流群](https://qm.qq.com/q/YqlBX8d2Ei)

## 环境与构建

- Halo **2.27.0 及以上**
- Java 21
- Node.js >= 22.12、pnpm 10（通过 `packageManager` 固定）

```sh
./gradlew build
```

Gradle 会安装并构建 UI、运行后端测试，并将前台和 Console 静态资源一起打包到 `build/libs/plugin-release-hub-1.0.0.jar`。普通构建无需运行 Halo 或 Docker。

在 Halo Console 的插件管理中上传此 JAR 并启用。Console 菜单为「版本中心」；前台由你在 Halo「页面」中插入编辑器组件后发布。

## 首次配置

1. 打开「插件 → 版本中心 → 设置」，填写站点公开 **HTTPS 地址**、品牌名称和可选 Logo。
2. 在 Console「版本中心 → 仓库账号」添加平台账号，填写名称，通过 Halo 官方 **Secret 组件**选择或创建一个包含 **`token`** 字段的密钥。保存时由后端读取 Token 验证并识别账号；GitLab 可填写公网自建站点根地址。
3. 点击「选择仓库」，加载并勾选需要同步的仓库，每批最多 50 个。可设置同步间隔（5–10080 分钟）与是否立即启用；已有仓库会标记并跳过。也可在「仓库管理 → 手动添加」中填写地址并使用同一个官方 Secret 组件，公开仓库允许不选密钥。
4. 点击「同步」，或等待后台任务。首次导入历史数据默认不发送通知，完整同步成功后才开启后续新版本通知。
5. 打开 Halo「页面 → 新建」，在默认编辑器工具箱选择 **「版本中心」**，或输入 `/版本中心` 插入组件。直接在组件中选择全部项目或指定项目、时间线/表格、默认版本范围和是否显示概览。
6. 保存并发布页面，按需添加到站点菜单。将该页面的完整 HTTPS 地址填写到「插件设置 → 站点与品牌 → 已发布的版本页面地址」，邮件和 Feed 的详情、确认、退订链接会使用此地址。

组件随页面内容保存，重新编辑可继续调整，后续版本同步后自动更新。支持同一页面放置多个组件，各自筛选与分页独立，不改变页面地址或标题。插件不再注册 `/releases` 等独立 HTML 展示路由；RSS/Atom 订阅端点继续保留。已有旧版 `/releases` 菜单请改为你发布的页面地址。

仓库的平台和地址创建后不可更换；更换项目时添加新仓库。可编辑名称、描述、频率、排序、令牌和启停状态。停用仓库会停止同步、前台展示和该项目通知，历史数据保留。

### Halo 插件与主题名称识别

仓库默认开启「自动识别 Halo 名称」。首次同步会读取默认分支的配置，按 Halo 资源类型校验后使用 **`spec.displayName`**：

- 插件：`src/main/resources/plugin.yaml`，兼容根目录 `plugin.yaml`。
- 主题：根目录 `theme.yaml`。上述路径同时兼容 `.yml` 后缀。

识别后的名称用于 Console、前台、RSS/Atom、邮件及 Webhook；手工填写的备用名称会保留。普通仓库、配置无效或读取失败时继续使用可用名称，名称识别失败不阻断版本同步。可在仓库编辑中关闭自动识别，使用手工项目名称。

识别结果（含未发现 Halo 配置）缓存 24 小时；网络或权限错误在后续同步中重试并保留此前识别的名称。重新开启自动识别会在下次同步重新检查。私有仓库使用已配置的账号或仓库 Secret，GitHub 文件查询沿用 API 代理。读取代码需要对应权限：GitHub **Contents（读取）**，GitLab `read_api` 或 `read_repository`，CNB **`repo-code:r`**，Gitee 仓库读取权限；只有 Release 权限时仍可同步版本，但可能无法识别名称。

## 仓库账号与 GitHub API 代理

一个账号可关联多个仓库。Token 由 Halo 官方 Secret 组件管理，支持新建、选择和编辑；账号/仓库管理请求只提交 Secret 名称，Token 内容由后端读取固定的 `token` 字段。账号管理接口返回 Secret 名称供编辑回显，不返回 Token 明文；公开接口不返回账号信息。更新所选 Secret 的 `token` 后，所有关联仓库同步使用新值。此前保存的账号与密钥引用可继续使用，无需重新填写。

仓库列表按平台分页加载，搜索针对已加载的数据；可继续「加载更多仓库」。选择会跨页保留，已添加的仓库无法重复勾选。导入前会重新查询仓库权限与路径，逐项返回成功、跳过或失败；失败项可以重试，不会重复创建已成功的仓库。**启用项目后版本日志会在前台公开展示，私有仓库也适用**，可在导入时关闭「添加后启用项目」。

已有手工添加的仓库可在编辑时选择账号，账号平台与站点必须匹配。仍有关联仓库时不能删除账号；解除全部关联后仅删除账号记录，所选 Halo Secret 会保留，可通过官方 Secret 管理界面单独维护。

| 平台 | 账号与仓库查询 | Token 读取范围 |
| --- | --- | --- |
| GitHub | `/user`、`/user/repos`，包括拥有、协作与组织授权的仓库 | 细粒度个人 Token 的 Metadata（读取）；同步 Release 需要 Contents（读取），组织授权与仓库范围也需开放 |
| Gitee | `/api/v5/user`、`/api/v5/user/repos` | 个人资料、仓库读取权限 |
| GitLab | `/api/v4/user`、`/api/v4/projects?membership=true`，支持嵌套组织 | `read_user`、`read_api` |
| CNB | `/user`、`/user/repos?role=Guest` | `account-profile:r`、`account-engage:r`、`repo-basic-info:r`、`repo-release:r` 与对应仓库使用范围 |

在「插件 → 版本中心 → 设置 → GitHub API 代理」填写 **API 代理根地址**。留空直连 `https://api.github.com`；只影响 GitHub 账号验证、仓库查询与版本同步，其他三平台保持直连，前台仓库地址及附件下载链接保持平台原始地址。

例如根地址 `https://github-api.example.com` 对应 `/user`、`/user/repos` 与 `/repos/{owner}/{repo}/releases`；也支持 `https://proxy.example.com/github` 或 `https://proxy.example.com/https://api.github.com` 这样的路径前缀。代理需为公网 HTTPS/443，透传认证和 API 请求头并返回 GitHub JSON。代理会收到账号 Token，因此请使用受信任的服务；普通下载加速地址未必支持 API。配置错误时同步会报错，不会自动绕过代理直连。

## 订阅

### RSS / Atom

无需登录，Feed 返回最近 50 个版本，默认仅正式版。

```text
/releases/rss.xml
/releases/atom.xml
/releases/projects/{projectId}/rss.xml
/releases/projects/{projectId}/atom.xml
```

可使用 `?prerelease=true` 包含预发布，或在聚合 Feed 上使用 `?project={projectId}`。前台「订阅更新」弹窗可以选择范围并复制完整 Feed 地址。

### 邮件

在 Halo「设置 → 通知设置 → 邮件通知」中配置并启用内置邮件通知器，然后在插件设置中勾选「开放邮件订阅」。**直接复用 Halo 的发件账号与发送能力，无需在插件里重复配置 SMTP。**

- 发件地址、发件名称、连接方式与凭据统一在 Halo 通知设置管理；修改后后续发送自动使用新配置。
- 用户输入邮箱后，需要在确认邮件的 24 小时有效链接中确认。公开接口不暴露邮箱或订阅列表。
- 可订阅全部或单个项目，并选择是否包含预发布。
- 每封版本通知带有安全退订入口；打开链接后还需确认，防止邮件扫描器误退订。
- 品牌名称、Logo、邮件主题及开场文字在插件设置中管理。主题支持 `{project}`、`{version}`。
- Console 订阅列表可停用、恢复已验证订阅、删除订阅和发送测试通知。

关闭邮件渠道时，前台不显示邮件选项；已有邮件任务不会绕过渠道开关。设置完成后，可通过一个已验证订阅发送测试通知并查看发送记录。

### Webhook

Console「订阅管理」可添加、修改或停用 Webhook，选择项目范围及预发布偏好。新渠道需要至少 24 个字符的签名密钥，保存在 Halo Secret 的 **`signingKey`** 字段。

回调只允许公网 HTTPS/443，不跟随重定向。下列请求头供接收端验证：

```text
X-Release-Hub-Id: <稳定事件 ID>
Idempotency-Key: <相同事件 ID>
X-Release-Hub-Timestamp: <Unix 秒>
X-Release-Hub-Signature: sha256=<HMAC-SHA256 十六进制值>
```

签名内容为 **`timestamp + "." + 原始请求体`**，密钥为配置的签名密钥。接收端应使用恒定时间比较校验签名、限制时间窗口，并持久化事件 ID 去重。

```json
{
  "event": "release.published",
  "id": "delivery-...",
  "timestamp": "...",
  "project": { "id": "project-...", "name": "Halo" },
  "release": {
    "tag": "v2.27.0",
    "title": "Halo 2.27.0",
    "publishedAt": "2026-10-14T00:00:00Z",
    "prerelease": false,
    "summary": "更新摘要",
    "url": "https://example.com/updates?rh-release=release-..."
  }
}
```

测试通知的事件类型为 `release.test`，`project`、`release` 为空对象。HTTP 2xx 视为成功，其余状态与连接失败进入重试；**最多 5 次**尝试，最终失败后可从发送记录中手动重新入队。

远端通知采用至少一次投递。HTTP/邮件服务已接收而插件未写入成功状态就崩溃时，可能重发；Webhook 接收端必须按事件 ID 去重。邮件的 MIME 与 Message-ID 由 Halo 生成。

## 前台与主题

- 在「插件 → 版本中心 → 设置 → 前台配色」设置 **主色、辅色**；默认绿色 `#00c16a` 与蓝色 `#3b82f6`。按钮、筛选、版本标记、下载入口与焦点提示使用统一配色，自动生成浅色/深色背景和可读文字色。
- Svelte 5 实现更新日志时间线与所有版本表格两种视图，支持正式/预发布筛选、项目切换、搜索、分页、版本详情、附件下载和订阅弹窗。日志保留完整 Markdown 排版，长内容可展开。
- 视图使用下划线标签，版本筛选使用浅底等宽分段控件；支持方向键与 Home/End 操作。概览卡片提供跟随鼠标位置的局部边框高光和淡色内光，使用主色/辅色。
- 提供筛选指示条滑动、滚动入场、视图切换、日志展开、弹窗开合及悬停/按下反馈；遵循系统「减少动态效果」偏好，此时卡片只保留静态高光。
- 前台不提供独立品牌顶栏，适合直接嵌入主题布局；视图切换与订阅入口位于内容区，深浅色跟随主题根节点或系统偏好。
- 项目筛选与订阅弹窗使用同一个 Svelte 项目选择器，菜单包含选中勾、悬停与开合效果，支持键盘操作和边界避让。选择交互基于 [Bits UI Select](https://bits-ui.com/docs/components/select)。
- 适配移动端，支持键盘操作与原生 dialog 焦点管理。
- Markdown 原始 HTML 转义后再清洗，移除脚本、不安全链接和外部图片追踪。
- 使用 Halo 默认编辑器的原生工具箱与斜杠命令保存 `<release-hub-embed>` 组件，页面外壳由当前主题负责，不需要复制模板或粘贴 HTML。
- 通过 Halo `TemplateHeadProcessor` 安装轻量加载器，仅在页面包含组件时加载前台 JS/CSS，兼容组件重连和页面节点替换。
- 公开浏览 API 位于 `/apis/api.release-hub.xhhao.com/v1alpha1`，主题可以直接使用 `projects`、`releases`、`releases/{name}` 与 `settings`。

Console 的页面也由 Svelte 实现，唯一 Vue 文件 `ConsoleBridge.vue` 负责接入 Halo Vue 路由和复用官方 `axiosInstance`。

## 权限与数据

非超级管理员需要获授「管理仓库与版本订阅」角色模板。该角色允许管理本插件业务数据；Halo 邮件通知设置与 Secret 的系统权限仍由 Halo 独立管理。新选 Secret 需具备该密钥的读取权限，官方组件的创建、编辑操作也遵循 Halo 自身的 Secret 权限；仅有插件管理权限不能借此读取其他 Secret。

数据保存在 Halo Extension 存储中，随 Halo 数据库一起备份；凭据和签名根密钥保存在 Halo Secret。请一并备份数据库与密钥，不要只备份插件 JAR。停用插件不清空业务数据和 Secret。

同步与发送记录当前保留全部历史。版本及任务规模增长时，需要结合站点数据量制定数据库备份与记录归档策略。当前实现适用于单实例 Halo，与 Halo 原生部署模型一致。

## 开发与验证

```sh
# 启动独立 Halo 开发实例（需要 Docker）
./gradlew haloServer -Pport=8098

# 后端/模板改动后重新构建与加载
./gradlew reloadPlugin

# 重生成 API 客户端，需要 Docker，生成目录不可手改
./gradlew generateApiClient

# UI 类型检查和两套生产构建
cd ui
pnpm install --frozen-lockfile
pnpm build

# 前台开发服务器，API 代理到 localhost:8098
pnpm dev:frontend
```


## 许可证

[GPL-3.0](LICENSE) © Handsome

插件 Logo 与菜单图标使用 Iconify 的 [lucide:package-check](https://icon-sets.iconify.design/lucide/package-check/)，图标遵循 [Lucide ISC 许可证](src/main/resources/licenses/lucide-LICENSE)，随插件本地提供。
