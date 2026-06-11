import { ref } from 'vue'
import type { Message } from '@/types'

// 模块级全局状态 — 保证全局只有一个右键菜单
export const ctxMenu = ref<{
  show: boolean
  x: number
  y: number
  type: 'channel' | 'message' | null
  channelId: number | null
  message: Message | null
}>({ show: false, x: 0, y: 0, type: null, channelId: null, message: null })

export function useContextMenu() {
  function openChannelMenu(e: MouseEvent, channelId: number | null) {
    ctxMenu.value = { show: true, x: e.clientX, y: e.clientY, type: 'channel', channelId, message: null }
  }

  function openMessageMenu(e: MouseEvent, msg: Message) {
    ctxMenu.value = { show: true, x: e.clientX, y: e.clientY, type: 'message', channelId: null, message: msg }
  }

  function closeMenu() {
    ctxMenu.value.show = false
  }

  return { ctxMenu, openChannelMenu, openMessageMenu, closeMenu }
}
