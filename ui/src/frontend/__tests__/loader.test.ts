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
