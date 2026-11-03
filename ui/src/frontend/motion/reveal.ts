export type RevealOptions = { delay?: number; distance?: number }
/** Entry and scroll motion use compositor properties, and honor the system preference. */
export function reveal(node: HTMLElement, options: RevealOptions = {}) {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)')
  let animation: Animation | undefined
  if (reduce.matches || !('IntersectionObserver' in window)) return {}
  node.style.opacity = '0'
  const observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return
      observer.disconnect()
      animation = node.animate(
        [
          { opacity: 0, transform: `translateY(${options.distance ?? 16}px)` },
          { opacity: 1, transform: 'translateY(0)' },
        ],
        {
          duration: 420,
          delay: Math.min(options.delay ?? 0, 240),
          easing: 'cubic-bezier(.2,.75,.25,1)',
          fill: 'both',
        },
      )
      animation.finished
        .then(() => {
          node.style.opacity = ''
          animation?.cancel()
        })
        .catch(() => {})
    },
    { threshold: 0.04, rootMargin: '0px 0px 35px 0px' },
  )
  observer.observe(node)
  function disableMotion() {
    if (reduce.matches) {
      observer.disconnect()
      animation?.cancel()
      node.style.opacity = ''
    }
  }
  reduce.addEventListener('change', disableMotion)
  return {
    destroy() {
      observer.disconnect()
      animation?.cancel()
      reduce.removeEventListener('change', disableMotion)
    },
  }
}
