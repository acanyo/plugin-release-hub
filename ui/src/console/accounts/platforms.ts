export const platforms: Record<string, string> = {
  GITHUB: 'GitHub',
  GITEE: 'Gitee',
  GITLAB: 'GitLab',
  CNB: 'CNB',
}

export const tokenHelp: Record<string, { text: string; url: string }> = {
  GITHUB: {
    text: '个人访问令牌需能读取所选仓库。细粒度 Token 需要 Metadata 读取权限，读取 Release 还需要 Contents 读取权限；组织仓库需完成组织授权。',
    url: 'https://github.com/settings/tokens',
  },
  GITEE: {
    text: '使用个人访问令牌，至少允许读取个人资料与仓库。仓库查询结果受 Token 的仓库访问权限限制。',
    url: 'https://gitee.com/profile/personal_access_tokens',
  },
  GITLAB: {
    text: '使用有 read_user 和 read_api 权限的个人访问令牌。支持 GitLab.com 和公网自建 GitLab 站点。',
    url: 'https://docs.gitlab.com/user/profile/personal_access_tokens/',
  },
  CNB: {
    text: '需要 account-profile:r、account-engage:r、repo-basic-info:r 与 repo-release:r 权限，并授权需要同步的仓库。',
    url: 'https://docs.cnb.cool/zh/guide/access-token.html',
  },
}
