import type { DocumentCard, TagColor } from '@/features/library/types'

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
