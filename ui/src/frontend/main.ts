import { mount, unmount } from 'svelte'
import App from './App.svelte'
import { readEmbedOptions } from '../shared/embed-options'
import '../shared/styles.css'
class ReleaseHubEmbed extends HTMLElement {
  static observedAttributes = ['project', 'view', 'channel', 'overview']
  private app?: ReturnType<typeof mount>
  connectedCallback() {
    if (this.app) return
    this.replaceChildren()
    this.style.display = 'block'
    this.app = mount(App, {
      target: this,
      props: {
        options: readEmbedOptions(this),
        primary: document.querySelector('release-hub-embed') === this,
      },
    })
  }
  attributeChangedCallback() {
    if (this.isConnected && this.app) {
      this.disconnectedCallback()
      this.connectedCallback()
    }
  }
  disconnectedCallback() {
    if (this.app) void unmount(this.app)
    this.app = undefined
  }
}
if (!customElements.get('release-hub-embed'))
  customElements.define('release-hub-embed', ReleaseHubEmbed)
