/** Follow a fine pointer inside the card without updating Svelte component state. */
export function spotlight(node: HTMLElement) {
  const motion = matchMedia(
    '(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)',
  )
  let frame = 0
  let clientX = 0
  let clientY = 0

  function move(event: PointerEvent) {
    if (!motion.matches || event.pointerType === 'touch') return
    clientX = event.clientX
    clientY = event.clientY
    if (frame) return
    frame = requestAnimationFrame(() => {
      frame = 0
      const bounds = node.getBoundingClientRect()
      node.style.setProperty('--rh-spotlight-x', `${clientX - bounds.left}px`)
      node.style.setProperty('--rh-spotlight-y', `${clientY - bounds.top}px`)
    })
  }
  function cancel() {
    cancelAnimationFrame(frame)
    frame = 0
  }
  function preferenceChanged() {
    cancel()
    node.style.removeProperty('--rh-spotlight-x')
    node.style.removeProperty('--rh-spotlight-y')
  }

  node.addEventListener('pointerenter', move)
  node.addEventListener('pointermove', move)
  node.addEventListener('pointerleave', cancel)
  motion.addEventListener('change', preferenceChanged)
  return {
    destroy() {
      cancel()
      node.removeEventListener('pointerenter', move)
      node.removeEventListener('pointermove', move)
      node.removeEventListener('pointerleave', cancel)
      motion.removeEventListener('change', preferenceChanged)
      preferenceChanged()
    },
  }
}
