// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Editor, Node } from '@halo-dev/richtext-editor'
import { ReleaseHubExtension } from '../release-hub-extension'
import { embedOptions } from '../../shared/embed-options'
import { pageLink } from '../../shared/page-links'

vi.mock('../ReleaseBlock.svelte', () => ({ default: {} }))
vi.mock('@halo-dev/richtext-editor', async () => ({
  ...(await import('@tiptap/core')),
  ToolboxItem: {},
}))
const documentNode = Node.create({ name: 'doc', topNode: true, content: 'block+' })
const text = Node.create({ name: 'text', group: 'inline' })
const paragraph = Node.create({
  name: 'paragraph',
  group: 'block',
  content: 'inline*',
  parseHTML: () => [{ tag: 'p' }],
  renderHTML: () => ['p', 0],
})
const extension = ReleaseHubExtension.extend({
  addNodeView() {
    return null
  },
})
let editor: Editor | undefined
function edit(content = '<p></p>') {
  return (editor = new Editor({ extensions: [documentNode, text, paragraph, extension], content }))
}
afterEach(() => editor?.destroy())

describe('版本中心编辑器组件', () => {
  it('persists project, view, channel and overview through save and reopen', () => {
    const value = edit(
      '<release-hub-embed project="project-1" view="list" channel="stable" overview="false"></release-hub-embed>',
    )
    const html = value.getHTML()
    value.commands.setContent(html)
    expect(value.state.doc.firstChild?.attrs).toEqual({
      project: 'project-1',
      view: 'list',
      channel: 'stable',
      overview: false,
    })
    expect(value.getHTML()).toBe(html)
  })
  it('uses safe defaults and never persists scripts or unrelated attributes', () => {
    const value = edit(
      '<release-hub-embed project="bad/path" view="unknown" channel="bad" onclick="evil()"></release-hub-embed>',
    )
    expect(value.state.doc.firstChild?.attrs).toEqual(embedOptions())
    expect(value.getHTML()).not.toContain('onclick')
    expect(value.getHTML()).not.toContain('bad/path')
  })
  it('toolbox inserts the actual serialized component', () => {
    const value = edit()
    const item = ReleaseHubExtension.options.getToolboxItems!({ editor: value })
    if (Array.isArray(item)) throw Error('Expected one item')
    expect(item.props.title).toBe('版本中心')
    ;(item.props.action as () => void)()
    expect(value.getHTML()).toContain('<release-hub-embed')
  })
  it('slash command replaces search text instead of saving a placeholder', () => {
    const value = edit('<p>/版本中心</p>')
    const item = ReleaseHubExtension.options.getCommandMenuItems!()
    if (Array.isArray(item)) throw Error('Expected one command')
    item.command({ editor: value, range: { from: 1, to: 7 } })
    expect(value.getHTML()).toContain('<release-hub-embed')
    expect(value.getText()).not.toContain('/版本中心')
  })
  it('keeps host path and unrelated query while clearing private action tokens from detail links', () => {
    const link = pageLink(
      { 'rh-release': 'release-2' },
      'https://example.com/my-page?lang=zh&rh-action=verify&rh-token=secret#section',
    )
    const parsed = new URL(link)
    expect(parsed.pathname).toBe('/my-page')
    expect(parsed.searchParams.get('lang')).toBe('zh')
    expect(parsed.searchParams.get('rh-release')).toBe('release-2')
    expect(parsed.searchParams.has('rh-token')).toBe(false)
  })
})
