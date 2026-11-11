import type { TopicNode } from '../types'

/** Dàn ý tối đa ba cấp (khớp Topic.MAX_DEPTH ở máy chủ). */
export const MAX_TOPIC_DEPTH = 3

/** Một chủ đề kèm vị trí của nó trong cây, để dựng các thao tác chuyển chỗ. */
export interface PlacedTopic {
  node: TopicNode
  depth: number
  parent: TopicNode | null
  /** Các chủ đề cùng cha, theo thứ tự. */
  siblings: TopicNode[]
  index: number
}

/** Duyệt cây theo thứ tự hiển thị. */
export function flattenTopics(nodes: TopicNode[], depth = 1, parent: TopicNode | null = null): PlacedTopic[] {
  return nodes.flatMap((node, index) => [
    { node, depth, parent, siblings: nodes, index },
    ...flattenTopics(node.children, depth + 1, node),
  ])
}

/** Số cấp của cây con tính từ chủ đề (chủ đề lá là 1). */
export function topicHeight(node: TopicNode): number {
  return 1 + Math.max(0, ...node.children.map(topicHeight))
}

/** {@code candidate} là chính {@code ancestor} hoặc nằm trong cây con của nó. */
export function isWithinTopic(ancestor: TopicNode, candidateId: string): boolean {
  return ancestor.id === candidateId || ancestor.children.some((child) => isWithinTopic(child, candidateId))
}

export function countTopics(nodes: TopicNode[]): number {
  return flattenTopics(nodes).length
}
