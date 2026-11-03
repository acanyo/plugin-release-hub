import { createVNode, h, render, type AppContext, type Component } from 'vue'
import { VModal, IconClose } from '@halo-dev/components'
import type { HaloSecretOptions, HaloUi } from './types'

/** Reuse the Console's registered FormKit inputs and providers from the Svelte UI. */
export function createHaloUi(context: AppContext, formkit: Component | string): HaloUi {
  return {
    secret(target, options) {
      function update(current: HaloSecretOptions) {
        const node = createVNode(formkit, {
          type: 'secret',
          name: 'credentialSecret',
          label: current.label,
          help: current.help,
          modelValue: current.value,
          disabled: current.disabled,
          requiredKeys: [{ key: 'token', help: '代码平台访问 Token' }],
          descriptionPreset: '版本中心仓库访问 Token',
          'onUpdate:modelValue': (value: unknown) =>
            current.change(typeof value === 'string' ? value : ''),
        })
        node.appContext = context
        render(node, target)
      }
      update(options)
      return { update, destroy: () => render(null, target) }
    },
    modal(target, options) {
      const attach = (element: HTMLElement) => (node: unknown) => {
        if (node instanceof HTMLElement && element.parentElement !== node) node.appendChild(element)
      }
      const node = createVNode(
        VModal,
        {
          title: options.title,
          width: options.width,
          mountToBody: true,
          centered: true,
          bodyClass: ['p-0'],
          class: 'rh-root rh-admin-modal',
          'aria-label': options.title,
          layerClosable: false,
          onKeyupCapture: (event: KeyboardEvent) => {
            if (event.key === 'Escape' && options.closeDisabled()) event.stopPropagation()
          },
          onClose: options.close,
        },
        {
          header: () =>
            h('div', { class: 'rh-halo-modal-header' }, [
              h('span', { class: 'modal-header-title' }, options.title),
              h(
                'button',
                {
                  class: 'rh-icon-button',
                  type: 'button',
                  'aria-label': '关闭弹窗',
                  onClick: () => {
                    if (!options.closeDisabled()) options.close()
                  },
                },
                [h(IconClose)],
              ),
            ]),
          default: () =>
            h('div', { class: 'rh-halo-modal-body' }, [
              options.description
                ? h('p', { class: 'rh-halo-modal-description' }, options.description)
                : null,
              h('div', { ref: attach(options.body) }),
            ]),
          ...(options.footer
            ? {
                footer: () =>
                  h('div', { class: 'rh-halo-modal-footer', ref: attach(options.footer!) }),
              }
            : {}),
        },
      )
      node.appContext = context
      render(node, target)
      return { destroy: () => render(null, target) }
    },
  }
}
