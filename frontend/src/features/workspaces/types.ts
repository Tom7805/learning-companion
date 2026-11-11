import type { ChunkLocation, DocumentCard, TagColor } from '@/features/library/types'

/** Biểu tượng của không gian học; khớp với enum WorkspaceIcon ở máy chủ. */
export type WorkspaceIcon =
  | 'BOOK'
  | 'CALCULATOR'
  | 'FLASK'
  | 'ATOM'
  | 'GLOBE'
  | 'CODE'
  | 'LANGUAGES'
  | 'CHART'
  | 'BRAIN'
  | 'PALETTE'
  | 'MUSIC'
  | 'BRIEFCASE'

export const WORKSPACE_ICONS: WorkspaceIcon[] = [
  'BOOK',
  'CALCULATOR',
  'FLASK',
  'ATOM',
  'GLOBE',
  'CODE',
  'LANGUAGES',
  'CHART',
  'BRAIN',
  'PALETTE',
  'MUSIC',
  'BRIEFCASE',
]

/** Không gian học dùng chung bảng màu với nhãn của kho tài liệu. */
export type WorkspaceColor = TagColor

export const WORKSPACE_NAME_MAX = 100
export const WORKSPACE_DESCRIPTION_MAX = 500

/** Tên và hình của một không gian học, đủ để nhận ra nó trong danh sách. */
export interface WorkspaceSummary {
  id: string
  name: string
  icon: WorkspaceIcon
  color: WorkspaceColor
}

export interface Workspace extends WorkspaceSummary {
  description: string
  /** Số tài liệu còn trong kho đang gắn với không gian học. */
  documentCount: number
  createdAt: string
  updatedAt: string
  lastOpenedAt?: string | null
}

export interface WorkspaceInput {
  name: string
  icon: WorkspaceIcon
  color: WorkspaceColor
  description: string
}

/** Một tài liệu của kho đang gắn với không gian học. */
export interface AttachedDocument {
  document: DocumentCard
  attachedAt: string
}

export interface AttachResult {
  /** Số liên kết mới. */
  attached: number
  /** Số tài liệu đã gắn từ trước. */
  alreadyAttached: number
}

/** Tài liệu hoặc thư mục cần biết đang được không gian học nào dùng. */
export interface UsageTarget {
  documentIds?: string[]
  folderId?: string
}

/** Một đoạn nguồn mà chủ đề trích tới (NCL-06-CN-002); {@code available} sai khi tài liệu đã bị xóa. */
export interface TopicSource {
  documentId?: string | null
  documentTitle: string
  location: ChunkLocation
  passage: number
  excerpt: string
  available: boolean
}

/** Một chủ đề trong dàn ý, kèm các chủ đề con. */
export interface TopicNode {
  id: string
  parentId?: string | null
  title: string
  goal: string
  /** Chủ đề do trợ lý đề xuất: mang nhãn "Do AI sinh". */
  aiGenerated: boolean
  /** Người học đã sửa tay chủ đề do trợ lý đề xuất. */
  edited: boolean
  /** Số tài liệu nguồn liên quan. */
  sourceCount: number
  sources: TopicSource[]
  children: TopicNode[]
}

export interface TopicTree {
  topics: TopicNode[]
  /** Số tài liệu sẵn sàng của không gian học: chưa có thì chưa đề xuất được dàn ý. */
  readySources: number
  /** Số chủ đề mới thêm ở lần đề xuất vừa rồi. */
  added?: number | null
  /** Bộ lọc an toàn chặn yêu cầu đề xuất. */
  safety?: 'BLOCKED' | 'SUPPORT' | null
}

export interface TopicInput {
  title: string
  goal?: string
  parentId?: string | null
}
