export const HALO_UI = Symbol('release-hub-halo-ui')

export interface HaloSecretOptions {
  value: string
  label: string
  help: string
  disabled: boolean
  change: (value: string) => void
}

export interface HaloModalOptions {
  title: string
  description?: string
  width: number
  body: HTMLElement
  footer?: HTMLElement
  close: () => void
  closeDisabled: () => boolean
}

export interface HaloUi {
  secret(
    target: HTMLElement,
    options: HaloSecretOptions,
  ): {
    update: (options: HaloSecretOptions) => void
    destroy: () => void
  }
  modal(target: HTMLElement, options: HaloModalOptions): { destroy: () => void }
}
