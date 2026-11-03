import { mount, unmount } from 'svelte'
import App from './App.svelte'
import '../shared/styles.css'
class ReleaseHubPage extends HTMLElement {
  private app?: ReturnType<typeof mount>
  connectedCallback() {
    if (this.app) return
    this.replaceChildren()
    this.app = mount(App, { target: this })
  }
  disconnectedCallback() {
    if (this.app) void unmount(this.app)
    this.app = undefined
  }
}
if (!customElements.get('release-hub-page'))
  customElements.define('release-hub-page', ReleaseHubPage)
