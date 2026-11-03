import { Node, ToolboxItem, type ExtensionOptions } from '@halo-dev/richtext-editor'
import { markRaw } from 'vue'
import { mount, unmount } from 'svelte'
import { writable } from 'svelte/store'
import VersionCenterIcon from '../shared/VersionCenterIcon'
import { embedOptions, readEmbedOptions, type EmbedOptions } from '../shared/embed-options'
import ReleaseBlock from './ReleaseBlock.svelte'

export const ReleaseHubExtension = Node.create<ExtensionOptions>({
  name: 'releaseHub',
  group: 'block',
  atom: true,
  draggable: true,
  selectable: true,
  addAttributes() {
    return Object.fromEntries(
      Object.entries(embedOptions()).map(([key, value]) => [
        key,
        {
          default: value,
          parseHTML: (element: Element) =>
            readEmbedOptions(element)[key as keyof ReturnType<typeof embedOptions>],
        },
      ]),
    )
  },
  parseHTML() {
    return [{ tag: 'release-hub-embed' }]
  },
  renderHTML({ node }) {
    const config = embedOptions(node.attrs)
    return ['release-hub-embed', { ...config, overview: String(config.overview) }]
  },
  addNodeView() {
    return ({ node, editor, getPos }) => {
      const dom = document.createElement('div')
      dom.contentEditable = 'false'
      dom.dataset.releaseHubEditor = 'true'
      const config = writable(embedOptions(node.attrs))
      const app = mount(ReleaseBlock, {
        target: dom,
        props: {
          config,
          change: (value: Partial<EmbedOptions>) => {
            const pos = getPos()
            if (typeof pos !== 'number' || editor.isDestroyed) return
            const current = editor.state.doc.nodeAt(pos)
            if (current?.type.name === 'releaseHub')
              editor.view.dispatch(
                editor.state.tr.setNodeMarkup(
                  pos,
                  undefined,
                  embedOptions({ ...current.attrs, ...value }),
                ),
              )
          },
          remove: () => {
            const pos = getPos()
            if (typeof pos === 'number' && !editor.isDestroyed) {
              const current = editor.state.doc.nodeAt(pos)
              if (current?.type.name === 'releaseHub')
                editor.view.dispatch(editor.state.tr.delete(pos, pos + current.nodeSize))
            }
          },
        },
      })
      return {
        dom,
        update(next) {
          if (next.type !== node.type) return false
          node = next
          config.set(embedOptions(next.attrs))
          return true
        },
        selectNode() {
          dom.classList.add('rh-editor-selected')
        },
        deselectNode() {
          dom.classList.remove('rh-editor-selected')
        },
        stopEvent: (event) => !event.type.startsWith('drag'),
        ignoreMutation: () => true,
        destroy() {
          void unmount(app)
        },
      }
    }
  },
  addOptions() {
    return {
      ...this.parent?.(),
      getToolboxItems({ editor }) {
        return {
          priority: 80,
          component: markRaw(ToolboxItem),
          props: {
            editor,
            icon: markRaw(VersionCenterIcon),
            title: '版本中心',
            description: '选择仓库，展示版本日志与订阅入口',
            action: () => editor.chain().focus().insertContent({ type: 'releaseHub' }).run(),
          },
        }
      },
      getCommandMenuItems() {
        return {
          priority: 80,
          icon: markRaw(VersionCenterIcon),
          title: '版本中心',
          keywords: ['版本', '更新日志', 'release', 'banben'],
          command: ({ editor, range }) =>
            editor.chain().focus().deleteRange(range).insertContent({ type: 'releaseHub' }).run(),
        }
      },
    }
  },
})
