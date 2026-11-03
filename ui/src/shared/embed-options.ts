export interface EmbedOptions {
  project: string
  view: 'timeline' | 'list'
  channel: 'all' | 'stable' | 'prerelease'
  overview: boolean
}

export function embedOptions(value: Record<string, unknown> = {}): EmbedOptions {
  return {
    project:
      typeof value.project === 'string' && /^[\w.-]{1,253}$/.test(value.project)
        ? value.project
        : '',
    view: value.view === 'list' ? 'list' : 'timeline',
    channel: value.channel === 'stable' || value.channel === 'prerelease' ? value.channel : 'all',
    overview: value.overview !== false && value.overview !== 'false',
  }
}

export function readEmbedOptions(element: Element): EmbedOptions {
  return embedOptions(
    Object.fromEntries(
      ['project', 'view', 'channel', 'overview'].map((key) => [key, element.getAttribute(key)]),
    ),
  )
}
