/** Links stay on the page chosen by its author; filters never replace the host page's URL. */
export function pageLink(values: Record<string, string> = {}, base = location.href): string {
  const url = new URL(base)
  for (const key of ['rh-release', 'rh-project', 'rh-action', 'rh-token'])
    url.searchParams.delete(key)
  for (const [key, value] of Object.entries(values)) if (value) url.searchParams.set(key, value)
  return url.toString()
}

export function releaseLink(name?: string): string {
  return pageLink({ 'rh-release': name || '' })
}

export function projectLink(name?: string): string {
  return pageLink({ 'rh-project': name || '' })
}
