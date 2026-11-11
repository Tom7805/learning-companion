import type { ConversationSummary } from '../types'

/** Nhóm của lịch sử hội thoại trên thanh bên (NCL-07-CN-003 TC-01), theo thứ tự hiển thị. */
export type HistoryGroup = 'pinned' | 'today' | 'yesterday' | 'week' | 'older'

export const HISTORY_GROUPS: HistoryGroup[] = ['pinned', 'today', 'yesterday', 'week', 'older']

const DAY = 24 * 60 * 60 * 1000

/** Nửa đêm (giờ máy người học) của ngày chứa {@code at}. */
function startOfDay(at: Date) {
  return new Date(at.getFullYear(), at.getMonth(), at.getDate()).getTime()
}

/** Nhóm theo ngày của lần dùng gần nhất, tính theo lịch của người học: hôm nay, hôm qua, bảy ngày qua, cũ hơn. */
export function historyGroupOf(conversation: ConversationSummary, now: Date): HistoryGroup {
  if (conversation.pinned) return 'pinned'
  const today = startOfDay(now)
  const at = new Date(conversation.updatedAt).getTime()
  if (at >= today) return 'today'
  if (at >= today - DAY) return 'yesterday'
  if (at >= today - 7 * DAY) return 'week'
  return 'older'
}

/** Chia lịch sử thành các nhóm có hội thoại, giữ nguyên thứ tự máy chủ trả về trong từng nhóm. */
export function groupConversations(conversations: ConversationSummary[], now = new Date()) {
  const groups = new Map<HistoryGroup, ConversationSummary[]>()
  for (const conversation of conversations) {
    const group = historyGroupOf(conversation, now)
    groups.set(group, [...(groups.get(group) ?? []), conversation])
  }
  return HISTORY_GROUPS.filter((group) => groups.has(group)).map((group) => ({ group, items: groups.get(group)! }))
}
