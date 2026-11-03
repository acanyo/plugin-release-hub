export const DEFAULT_PRIMARY = '#00c16a'
export const DEFAULT_SECONDARY = '#3b82f6'
export const DEFAULT_DARK_BACKGROUND = '#202124'
type Rgb = [number, number, number]

export function normalizeColor(value: string | undefined, fallback: string): string {
  const hex = value?.trim().toLowerCase()
  if (hex && /^#[0-9a-f]{6}$/.test(hex)) return hex
  if (hex && /^#[0-9a-f]{3}$/.test(hex)) return '#' + [...hex.slice(1)].map((c) => c + c).join('')
  return fallback
}
function rgb(hex: string): Rgb {
  return [1, 3, 5].map((start) => parseInt(hex.slice(start, start + 2), 16)) as Rgb
}
function luminance(color: Rgb): number {
  const [r, g, b] = color.map((value) => {
    const s = value / 255
    return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!
}
function contrast(a: Rgb, b: Rgb): number {
  const first = luminance(a),
    second = luminance(b)
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05)
}
export function contrastRatio(first: string, second: string): number {
  return contrast(rgb(first), rgb(second))
}
export function foreground(background: string): string {
  const color = rgb(background)
  if (contrast(color, rgb('#0f172a')) >= 4.5) return '#0f172a'
  if (contrast(color, rgb('#ffffff')) >= 4.5) return '#ffffff'
  return '#000000'
}
function readable(color: string, background: string): string {
  const start = rgb(color),
    bg = rgb(background)
  if (contrast(start, bg) >= 4.5) return color
  const end: Rgb = luminance(bg) > 0.4 ? [0, 0, 0] : [255, 255, 255]
  for (let step = 1; step <= 100; step++) {
    const mixed = start.map((value, index) =>
      Math.round(value + ((end[index]! - value) * step) / 100),
    ) as Rgb
    if (contrast(mixed, bg) >= 4.5)
      return '#' + mixed.map((value) => value.toString(16).padStart(2, '0')).join('')
  }
  return luminance(bg) > 0.4 ? '#000000' : '#ffffff'
}
/** Two persisted inputs generate every foreground, tint and dark-mode variant. */
export function paletteStyle(
  primaryValue?: string,
  secondaryValue?: string,
  dark = false,
  surface?: string,
): string {
  const primary = normalizeColor(primaryValue, DEFAULT_PRIMARY)
  const secondary = normalizeColor(secondaryValue, DEFAULT_SECONDARY)
  const background = normalizeColor(surface, dark ? DEFAULT_DARK_BACKGROUND : '#ffffff')
  const values: Record<string, string> = {
    '--rh-bg': background,
    '--rh-primary': primary,
    '--rh-primary-ink': foreground(primary),
    '--rh-primary-text': readable(primary, background),
    '--rh-secondary': secondary,
    '--rh-secondary-ink': foreground(secondary),
    '--rh-secondary-text': readable(secondary, background),
  }
  return Object.entries(values)
    .map(([key, value]) => `${key}:${value}`)
    .join(';')
}
