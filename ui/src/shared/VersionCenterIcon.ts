import { h } from 'vue'
import markUrl from '../../../src/main/resources/logo-mark.svg'

/** Halo's menu uses the monochrome Iconify mark without the plugin logo's background. */
export default function VersionCenterIcon() {
  return h('img', {
    src: markUrl,
    alt: '',
    'aria-hidden': 'true',
    draggable: false,
    style: {
      display: 'inline-block',
      width: '1em',
      height: '1em',
      flexShrink: 0,
      verticalAlign: 'middle',
    },
  })
}
