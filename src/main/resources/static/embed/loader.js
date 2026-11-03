(() => {
  if (window.__releaseHubLoader) return;
  window.__releaseHubLoader = true;
  const source = new URL(document.currentScript.src);
  const base = new URL("../frontend/", source);
  const stylesheetUrl = new URL("release-hub.css" + source.search, base).href;
  let scriptRequested = false;
  let failed = false;

  function offerRetry() {
    document.querySelectorAll("release-hub-embed").forEach((element) => {
      if (element.querySelector("[data-rh-retry]")) return;
      const button = document.createElement("button");
      button.type = "button";
      button.dataset.rhRetry = "";
      button.textContent = "版本中心加载失败，点击重试";
      button.onclick = () => {
        failed = false;
        document
          .querySelectorAll("release-hub-embed [data-rh-retry]")
          .forEach((button) => button.remove());
        scan();
      };
      element.append(button);
    });
  }

  function assetFailed(asset) {
    failed = true;
    asset.remove();
    offerRetry();
  }

  function ensureStyles() {
    const existing = Array.from(document.querySelectorAll('link[rel~="stylesheet"]')).find(
      (link) => link.href === stylesheetUrl,
    );
    if (existing) return;
    const style = document.createElement("link");
    style.rel = "stylesheet";
    style.href = stylesheetUrl;
    style.addEventListener("error", () => assetFailed(style), { once: true });
    document.head.append(style);
  }

  function scan() {
    if (!document.querySelector("release-hub-embed")) return;
    if (failed) {
      offerRetry();
      return;
    }
    // Swup may remove dynamically inserted head assets while the module stays loaded.
    // Restore CSS independently of the module's one-time initialization.
    ensureStyles();
    if (scriptRequested || customElements.get("release-hub-embed")) return;
    scriptRequested = true;
    const script = document.createElement("script");
    script.type = "module";
    script.src = new URL("release-hub.js" + source.search, base).href;
    script.addEventListener(
      "error",
      () => {
        scriptRequested = false;
        assetFailed(script);
      },
      { once: true },
    );
    document.head.append(script);
  }
  scan();
  new MutationObserver(scan).observe(document.documentElement, { childList: true, subtree: true });
})();
