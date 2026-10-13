import { useSyncExternalStore } from 'react'
import { localStore } from '@/shared/lib/storage'

/** Một nội dung người học vừa mở, hiện ở mục "Gần đây" của thanh bên. */
export interface RecentItem {
  id: string
  kind: 'document'
  title: string
  to: string
  openedAt: string
}

const LIMIT = 6
const keyOf = (accountId: string) => `lc:recent:${accountId}`
const listeners = new Set<() => void>()
const cache = new Map<string, RecentItem[]>()

function read(accountId: string): RecentItem[] {
  if (!cache.has(accountId)) {
    const stored = localStore.get<RecentItem[]>(keyOf(accountId))
    cache.set(accountId, Array.isArray(stored) ? stored.slice(0, LIMIT) : [])
  }
  return cache.get(accountId)!
}

function write(accountId: string, items: RecentItem[]) {
  cache.set(accountId, items)
  if (items.length) localStore.set(keyOf(accountId), items)
  else localStore.remove(keyOf(accountId))
  listeners.forEach((listener) => listener())
}

/**
 * Mục "Gần đây" (NCL-02-CN-001): lưu trên máy theo từng tài khoản, nên người khác đăng nhập cùng máy không thấy.
 * Xóa khi đăng xuất; nội dung đã bị xóa hoặc không còn mở được thì tự rời khỏi danh sách.
 */
export const recentStore = {
  record(accountId: string, item: Omit<RecentItem, 'openedAt'>) {
    const rest = read(accountId).filter((existing) => !(existing.kind === item.kind && existing.id === item.id))
    write(accountId, [{ ...item, openedAt: new Date().toISOString() }, ...rest].slice(0, LIMIT))
  },
  forget(accountId: string, kind: RecentItem['kind'], id: string) {
    const current = read(accountId)
    const next = current.filter((item) => !(item.kind === kind && item.id === id))
    if (next.length !== current.length) write(accountId, next)
  },
  clear(accountId: string) {
    write(accountId, [])
  },
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  /** Chỉ dùng trong kiểm thử. */
  reset() {
    cache.clear()
  },
}

const EMPTY: RecentItem[] = []

export function useRecentItems(accountId: string | undefined) {
  return useSyncExternalStore(
    recentStore.subscribe,
    () => (accountId ? read(accountId) : EMPTY),
    () => EMPTY,
  )
}
