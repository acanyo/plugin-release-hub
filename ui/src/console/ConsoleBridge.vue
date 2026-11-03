<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, getCurrentInstance, resolveComponent } from 'vue'
import { mount, unmount, type Component } from 'svelte'
import { axiosInstance } from '@halo-dev/api-client'
import Console from './Console.svelte'
import { createHaloUi } from './halo-ui/create-halo-ui'
const context = getCurrentInstance()!.appContext
const haloUi = createHaloUi(context, resolveComponent('FormKit'))
const target = ref<HTMLElement>()
let app: ReturnType<typeof mount> | undefined
onMounted(() => {
  if (target.value)
    app = mount(Console as Component, {
      target: target.value,
      props: { axios: axiosInstance, haloUi },
    })
})
onBeforeUnmount(() => {
  if (app) void unmount(app)
})
</script>
<template><div ref="target" /></template>
