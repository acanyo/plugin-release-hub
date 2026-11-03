import { describe, expect, it } from 'vitest'
import { paletteStyle, normalizeColor, foreground, contrastRatio } from '../appearance/palette'
function variable(style: string, name: string) {
  return style
    .split(';')
    .find((v) => v.startsWith(name + ':'))!
    .split(':')[1]!
}
describe('two-color frontend palette', () => {
  it('keeps supplied primary and secondary colors independent', () => {
    const style = paletteStyle('#7c3aed', '#f97316')
    expect(variable(style, '--rh-primary')).toBe('#7c3aed')
    expect(variable(style, '--rh-secondary')).toBe('#f97316')
  })
  it('only accepts hex colors and rejects CSS injection', () => {
    expect(normalizeColor('#abC', '#00c16a')).toBe('#aabbcc')
    expect(normalizeColor('red;background:url(https://example.com)', '#00c16a')).toBe('#00c16a')
  })
  it.each(['#00c16a', '#3b82f6', '#ffffff', '#000000', '#ffff00', '#7c3aed', '#f97316', '#808080'])(
    'maintains readable text and button contrast for %s',
    (color) => {
      expect(contrastRatio(color, foreground(color))).toBeGreaterThanOrEqual(4.5)
      for (const dark of [false, true]) {
        const style = paletteStyle(color, color, dark)
        const background = dark ? '#0f172a' : '#ffffff'
        expect(
          contrastRatio(variable(style, '--rh-primary-text'), background),
        ).toBeGreaterThanOrEqual(4.5)
        expect(
          contrastRatio(variable(style, '--rh-secondary-text'), background),
        ).toBeGreaterThanOrEqual(4.5)
      }
    },
  )
})
