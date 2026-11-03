// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { followHostTheme, readHostTheme, type HostTheme } from '../appearance/theme'
import { DEFAULT_DARK_BACKGROUND } from '../appearance/palette'

let media: EventTarget & { matches: boolean }
let cleanup: (() => void)[] = []
function component() {
  const host = document.createElement('release-hub-embed')
  const root = document.createElement('div')
  root.dataset.theme = 'light'
  host.append(root)
  document.body.append(host)
  return root
}
function observe(root: HTMLElement) {
  const change = vi.fn<(theme: HostTheme) => void>()
  const handle = followHostTheme(root, change)
  cleanup.push(handle.destroy)
  return { change, destroy: handle.destroy }
}
beforeEach(() => {
  document.documentElement.className = ''
  for (const name of ['data-theme', 'data-color-scheme', 'data-mode', 'style'])
    document.documentElement.removeAttribute(name)
  document.body.replaceWith(document.createElement('body'))
  media = Object.assign(new EventTarget(), { matches: false })
  vi.stubGlobal('matchMedia', () => media)
})
afterEach(() => {
  cleanup.forEach((destroy) => destroy())
  cleanup = []
  vi.unstubAllGlobals()
})

describe('host theme synchronization', () => {
  it.each(['data-theme', 'data-color-scheme', 'data-mode'])(
    'follows %s changes in both directions without a reload',
    async (attribute) => {
      const root = component()
      const { change } = observe(root)
      expect(change).toHaveBeenLastCalledWith({ dark: false, background: '#ffffff' })
      document.documentElement.setAttribute(attribute, 'dark')
      await vi.waitFor(() =>
        expect(change).toHaveBeenLastCalledWith({
          dark: true,
          background: DEFAULT_DARK_BACKGROUND,
        }),
      )
      media.matches = true
      document.documentElement.setAttribute(attribute, 'light')
      await vi.waitFor(() =>
        expect(change).toHaveBeenLastCalledWith({ dark: false, background: '#ffffff' }),
      )
    },
  )
  it('uses the closest explicit theme and never reads its own output', async () => {
    document.documentElement.classList.add('dark')
    document.body.dataset.theme = 'light'
    const root = component()
    root.dataset.theme = 'dark'
    const { change } = observe(root)
    expect(change.mock.lastCall?.[0].dark).toBe(false)
    document.body.removeAttribute('data-theme')
    await vi.waitFor(() => expect(change.mock.lastCall?.[0].dark).toBe(true))
  })
  it('follows body classes and inline CSS color-scheme', async () => {
    const { change } = observe(component())
    document.body.className = 'dark'
    await vi.waitFor(() => expect(change.mock.lastCall?.[0].dark).toBe(true))
    document.body.className = ''
    document.body.style.colorScheme = 'light'
    await vi.waitFor(() => expect(change.mock.lastCall?.[0].dark).toBe(false))
    document.body.style.colorScheme = 'dark'
    await vi.waitFor(() => expect(change.mock.lastCall?.[0].dark).toBe(true))
  })
  it('follows system changes only when the host has no explicit mode', () => {
    const { change } = observe(component())
    media.matches = true
    media.dispatchEvent(new Event('change'))
    expect(change.mock.lastCall?.[0].dark).toBe(true)
    document.documentElement.dataset.colorScheme = 'light'
    media.dispatchEvent(new Event('change'))
    expect(change.mock.lastCall?.[0].dark).toBe(false)
  })
  it('uses the nearest opaque host surface through transparent wrappers', async () => {
    document.documentElement.style.backgroundColor = '#101010'
    document.body.style.backgroundColor = '#202124'
    document.body.dataset.mode = 'dark'
    const root = component()
    const { change } = observe(root)
    expect(change).toHaveBeenLastCalledWith({ dark: true, background: '#202124' })
    root.parentElement!.style.backgroundColor = '#303035'
    await vi.waitFor(() => expect(change.mock.lastCall?.[0].background).toBe('#303035'))
    root.parentElement!.style.backgroundColor = 'rgba(0,0,0,0.5)'
    await vi.waitFor(() => expect(change.mock.lastCall?.[0].background).toBe('#202124'))
  })
  it('reads the current mode again after Swup replaces the content and body', async () => {
    const first = observe(component())
    first.destroy()
    document.body.replaceWith(document.createElement('body'))
    document.documentElement.dataset.mode = 'dark'
    const current = observe(component())
    expect(current.change.mock.lastCall?.[0].dark).toBe(true)
    document.documentElement.dataset.mode = 'light'
    await vi.waitFor(() => expect(current.change.mock.lastCall?.[0].dark).toBe(false))
    expect(first.change).toHaveBeenCalledTimes(1)
  })
  it('disconnects mutation and system listeners when unmounted', async () => {
    const { change, destroy } = observe(component())
    destroy()
    document.documentElement.dataset.mode = 'dark'
    media.matches = true
    media.dispatchEvent(new Event('change'))
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(change).toHaveBeenCalledTimes(1)
  })
  it('falls back to the system for auto mode and dual color schemes', () => {
    document.documentElement.dataset.mode = 'auto'
    document.body.style.colorScheme = 'light dark'
    expect(readHostTheme(component(), true).dark).toBe(true)
    expect(readHostTheme(component(), false).dark).toBe(false)
  })
})
