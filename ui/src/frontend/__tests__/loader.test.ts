// @vitest-environment jsdom
/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { afterEach, expect, it, vi } from 'vitest'
const source = readFileSync('../src/main/resources/static/embed/loader.js', 'utf8')

let observer: MutationObserver | undefined
function setup(markup = '') {
  document.head.innerHTML = ''
  document.body.innerHTML = markup
  const script = document.createElement('script')
  script.src = 'https://example.com/plugins/release-hub/assets/embed/loader.js?v=1.0.0'
  Object.defineProperty(document, 'currentScript', { value: script, configurable: true })
  delete (window as unknown as Record<string, unknown>).__releaseHubLoader
  const NativeObserver = MutationObserver
  vi.stubGlobal(
    'MutationObserver',
    class extends NativeObserver {
      constructor(callback: MutationCallback) {
        super(callback)
        observer = this
      }
    },
  )
  window.eval(source)
}
afterEach(() => {
  observer?.disconnect()
  vi.unstubAllGlobals()
})
it('does not load the application on unrelated pages', () => {
  setup('<p>Normal page</p>')
  expect(document.head.querySelector('script')).toBeNull()
  expect(document.head.querySelector('link')).toBeNull()
})
it('loads once when components are inserted after PJAX navigation', async () => {
  setup()
  document.body.innerHTML =
    '<release-hub-embed></release-hub-embed><release-hub-embed></release-hub-embed>'
  await vi.waitFor(() => expect(document.head.querySelectorAll('script')).toHaveLength(1))
  expect(document.head.querySelector('script')?.src).toBe(
    'https://example.com/plugins/release-hub/assets/frontend/release-hub.js?v=1.0.0',
  )
  expect(document.head.querySelectorAll('link')).toHaveLength(1)
})
it('offers a retry after an asset error', async () => {
  setup('<release-hub-embed></release-hub-embed>')
  document.head.querySelector('script')!.dispatchEvent(new Event('error'))
  const button = document.querySelector<HTMLButtonElement>('[data-rh-retry]')!
  expect(button).not.toBeNull()
  button.click()
  expect(document.head.querySelectorAll('script')).toHaveLength(1)
})
it('restores styles removed by Swup head replacement without importing the module again', async () => {
  setup('<release-hub-embed></release-hub-embed>')
  const script = document.head.querySelector('script')!
  const href = document.head.querySelector('link')!.href
  for (let visit = 0; visit < 3; visit++) {
    document.body.innerHTML = '<p>Another page</p>'
    document.head.querySelector('link')!.remove()
    await new Promise((resolve) => setTimeout(resolve, 0))
    document.body.innerHTML = '<release-hub-embed></release-hub-embed>'
    await vi.waitFor(() => expect(document.head.querySelector('link')?.href).toBe(href))
    expect(document.head.querySelectorAll('link')).toHaveLength(1)
    expect(document.head.querySelectorAll('script')).toHaveLength(1)
    expect(document.head.querySelector('script')).toBe(script)
  }
})
it('reuses an existing stylesheet supplied by the theme', () => {
  setup()
  const link = document.createElement('link')
  link.rel = 'stylesheet'
  link.href = 'https://example.com/plugins/release-hub/assets/frontend/release-hub.css?v=1.0.0'
  document.head.append(link)
  document.body.innerHTML = '<release-hub-embed></release-hub-embed>'
  return vi.waitFor(() => {
    expect(document.head.querySelectorAll('script')).toHaveLength(1)
    expect(document.head.querySelectorAll('link')).toHaveLength(1)
    expect(document.head.querySelector('link')).toBe(link)
  })
})
it('restores CSS removed while the component is still mounted', async () => {
  setup('<release-hub-embed></release-hub-embed>')
  document.head.querySelector('link')!.remove()
  await vi.waitFor(() => expect(document.head.querySelectorAll('link')).toHaveLength(1))
  expect(document.head.querySelectorAll('script')).toHaveLength(1)
})
it('does not automatically retry failed assets on DOM changes', async () => {
  setup('<release-hub-embed></release-hub-embed>')
  document.head.querySelector('script')!.dispatchEvent(new Event('error'))
  document.body.append(document.createElement('p'))
  await new Promise((resolve) => setTimeout(resolve, 0))
  expect(document.head.querySelector('script')).toBeNull()
  expect(document.querySelectorAll('[data-rh-retry]')).toHaveLength(1)
})
it('retries only the stylesheet when CSS fails after the module has loaded', async () => {
  setup('<release-hub-embed></release-hub-embed>')
  const script = document.head.querySelector('script')!
  document.head.querySelector('link')!.dispatchEvent(new Event('error'))
  await new Promise((resolve) => setTimeout(resolve, 0))
  expect(document.head.querySelector('link')).toBeNull()
  document.querySelector<HTMLButtonElement>('[data-rh-retry]')!.click()
  expect(document.head.querySelectorAll('link')).toHaveLength(1)
  expect(document.head.querySelector('script')).toBe(script)
  expect(document.querySelector('[data-rh-retry]')).toBeNull()
})
