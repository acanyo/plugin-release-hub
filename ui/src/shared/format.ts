export function date(value?: string, short = false): string {
  if (!value) return '尚未同步'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    ...(short ? {} : { hour: '2-digit', minute: '2-digit' }),
  }).format(new Date(value))
}
export function errorMessage(error: unknown): string {
  const e = error as {
    response?: { data?: { message?: string; detail?: string } }
    message?: string
  }
  return e.response?.data?.message || e.response?.data?.detail || '操作失败，请稍后重试。'
}
export function bytes(size = 0): string {
  if (!size) return ''
  return size > 1048576 ? `${(size / 1048576).toFixed(1)} MB` : `${Math.ceil(size / 1024)} KB`
}
export function safeLink(value?: string): string {
  if (!value) return ''
  try {
    const u = new URL(value)
    return u.protocol === 'https:' && !u.username && !u.password ? u.href : ''
  } catch {
    return ''
  }
}
