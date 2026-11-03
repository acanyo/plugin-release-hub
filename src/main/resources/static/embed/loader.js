(() => {
  if (window.__releaseHubLoader) return
  window.__releaseHubLoader = true
  const source = new URL(document.currentScript.src)
  const base = new URL('../frontend/', source)
  let loading = false
  function scan() {
    if (loading || !document.querySelector('release-hub-embed')) return
    loading = true
    const style = document.createElement('link')
    style.rel = 'stylesheet'
    style.href = new URL('release-hub.css' + source.search, base).href
    const script = document.createElement('script')
    script.type = 'module'
    script.src = new URL('release-hub.js' + source.search, base).href
    script.addEventListener('error', () => {
      loading = false
      script.remove()
      style.remove()
      document.querySelectorAll('release-hub-embed').forEach(element => {
        if (element.querySelector('[data-rh-retry]')) return
        const button = document.createElement('button')
        button.type = 'button'
        button.dataset.rhRetry = ''
        button.textContent = '版本中心加载失败，点击重试'
        button.onclick = () => { button.remove(); scan() }
        element.append(button)
      })
    })
    document.head.append(style, script)
  }
  scan()
  new MutationObserver(scan).observe(document.documentElement, { childList: true, subtree: true })
})()
