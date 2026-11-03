import { DEFAULT_DARK_BACKGROUND } from './palette'

export interface HostTheme {
  dark: boolean
  background: string
}

const themeAttributes = ['data-color-scheme', 'data-theme', 'data-mode']

function explicitMode(element: HTMLElement): boolean | undefined {
  for (const attribute of themeAttributes) {
    const value = element.getAttribute(attribute)?.toLowerCase()
    if (value === 'dark' || value === 'light') return value === 'dark'
  }
  if (element.classList.contains('dark')) return true
  if (element.classList.contains('light')) return false
}

/** Computed backgrounds may be transparent; only an opaque surface can stand on its own. */
function opaqueColor(value: string): string | undefined {
  const match = value.match(
    /^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,/]\s*([\d.]+))?\s*\)$/,
  )
  if (!match || (match[4] !== undefined && Number(match[4]) !== 1)) return
  return (
    '#' +
    match
      .slice(1, 4)
      .map((channel) => Math.round(Number(channel)).toString(16).padStart(2, '0'))
      .join('')
  )
}

export function readHostTheme(host: HTMLElement, prefersDark: boolean): HostTheme {
  const ancestors: HTMLElement[] = []
  // Exclude the component root: its own data-theme and color-scheme are outputs.
  for (let parent = host.parentElement; parent; parent = parent.parentElement)
    ancestors.push(parent)
  const explicit = ancestors.map(explicitMode).find((mode) => mode !== undefined)
  const scheme = getComputedStyle(host.parentElement || document.documentElement).colorScheme
  const dark =
    explicit ??
    (scheme === 'dark' || scheme === 'only dark'
      ? true
      : scheme === 'light' || scheme === 'only light'
        ? false
        : prefersDark)
  const background =
    ancestors
      .map((element) => opaqueColor(getComputedStyle(element).backgroundColor))
      .find((color) => color !== undefined) || (dark ? DEFAULT_DARK_BACKGROUND : '#ffffff')
  return { dark, background }
}

/** Each mounted component follows its current ancestors; Swup reconnection installs fresh listeners. */
export function followHostTheme(host: HTMLElement, change: (theme: HostTheme) => void) {
  const media = matchMedia('(prefers-color-scheme: dark)')
  const observer = new MutationObserver(update)
  const ancestors = new Set<HTMLElement>()
  let previous: HostTheme | undefined
  function update() {
    const next = readHostTheme(host, media.matches)
    if (previous?.dark === next.dark && previous.background === next.background) return
    previous = next
    change(next)
  }
  // The final background is available after a theme's color transition completes.
  function transitionEnd(event: TransitionEvent) {
    if (ancestors.has(event.target as HTMLElement) && event.propertyName === 'background-color')
      update()
  }
  for (let parent = host.parentElement; parent; parent = parent.parentElement) {
    ancestors.add(parent)
    observer.observe(parent, {
      attributes: true,
      attributeFilter: ['class', 'style', ...themeAttributes],
    })
  }
  document.addEventListener('transitionend', transitionEnd)
  media.addEventListener('change', update)
  update()
  return {
    destroy() {
      observer.disconnect()
      document.removeEventListener('transitionend', transitionEnd)
      media.removeEventListener('change', update)
    },
  }
}
