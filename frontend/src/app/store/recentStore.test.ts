import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { recentStore, useRecentItems, type RecentItem } from './recentStore'

const LAN = 'account-lan'
const MINH = 'account-minh'

const doc = (id: string, title = `Tài liệu ${id}`): Omit<RecentItem, 'openedAt'> => ({
  id,
  kind: 'document',
  title,
  to: `/library/${id}`,
})

const ids = (list: RecentItem[]) => list.map((item) => item.id)

describe('recentStore', () => {
  it('mở lại một tài liệu thì đưa lên đầu, không lặp, và thanh bên cập nhật ngay', () => {
    const { result } = renderHook(() => useRecentItems(LAN))
    expect(result.current).toEqual([])

    act(() => {
      recentStore.record(LAN, doc('a'))
      recentStore.record(LAN, doc('b'))
      recentStore.record(LAN, doc('a', 'Tên mới'))
    })

    expect(result.current.map((item) => [item.id, item.title])).toEqual([
      ['a', 'Tên mới'],
      ['b', 'Tài liệu b'],
    ])
  })

  it('chỉ giữ sáu mục gần nhất và còn nguyên khi mở lại ứng dụng', () => {
    for (const id of ['1', '2', '3', '4', '5', '6', '7']) recentStore.record(LAN, doc(id))

    recentStore.reset()
    const { result } = renderHook(() => useRecentItems(LAN))

    expect(ids(result.current)).toEqual(['7', '6', '5', '4', '3', '2'])
  })

  it('mỗi tài khoản có danh sách riêng trên cùng một máy', () => {
    recentStore.record(LAN, doc('cua-lan'))
    recentStore.record(MINH, doc('cua-minh'))

    expect(ids(renderHook(() => useRecentItems(LAN)).result.current)).toEqual(['cua-lan'])
    expect(ids(renderHook(() => useRecentItems(MINH)).result.current)).toEqual(['cua-minh'])
    expect(renderHook(() => useRecentItems(undefined)).result.current).toEqual([])
  })

  it('tài liệu không còn thì rời danh sách; đăng xuất thì xóa sạch khỏi máy', () => {
    recentStore.record(LAN, doc('a'))
    recentStore.record(LAN, doc('b'))
    const { result } = renderHook(() => useRecentItems(LAN))

    act(() => recentStore.forget(LAN, 'document', 'a'))
    expect(ids(result.current)).toEqual(['b'])

    act(() => recentStore.clear(LAN))
    expect(result.current).toEqual([])
    expect(window.localStorage.getItem(`lc:recent:${LAN}`)).toBeNull()
  })
})
