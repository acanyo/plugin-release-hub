import { definePlugin } from '@halo-dev/ui-shared'
import VersionCenterIcon from './shared/VersionCenterIcon'
import { markRaw } from 'vue'
export default definePlugin({
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/release-hub',
        name: 'ReleaseHub',
        component: () => import('./console/ConsoleBridge.vue'),
        meta: {
          title: '版本中心',
          searchable: true,
          permissions: ['plugin:release-hub:manage'],
          menu: {
            name: '版本中心',
            group: 'content',
            icon: markRaw(VersionCenterIcon),
            priority: 20,
          },
        },
      },
    },
  ],
})
