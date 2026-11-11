import type { ChunkLocation, DocumentStatus, DocumentType } from '@/features/library/types'
import type { SafetyOutcome } from '@/features/safety'

/** Trạng thái một tin nhắn; khớp với Message.Status ở máy chủ. */
export type MessageStatus =
  | 'SENT'
  | 'STREAMING'
  | 'COMPLETE'
  | 'STOPPED'
  | 'FAILED'
  | 'BLOCKED'
  | 'SUPPORT'
  | 'NOT_FOUND'
  | 'NO_SOURCES'

/** Một trích dẫn: tài liệu (trống nếu đã bị xóa hẳn), vị trí và thứ tự đoạn để mở khung xem đúng chỗ. */
export interface Citation {
  id?: string
  number: number
  documentId?: string | null
  documentTitle: string
  location: ChunkLocation
  passage: number
  excerpt: string
}

export interface ChatMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  status: MessageStatus
  documentIds: string[]
  suggestions: string[]
  errorCode?: string | null
  citations: Citation[]
  createdAt: string
  /** Chỉ có trong phiên: bộ lọc an toàn can thiệp. */
  safety?: SafetyOutcome | null
  /** Chỉ có trong phiên: tài liệu khác của kho có nói về câu hỏi, gợi ý chọn thêm (TC-04). */
  elsewhere?: { id: string; title: string }[]
}

/** Tài liệu trong phạm vi hỏi đáp. */
export interface ScopeDocument {
  id: string
  title: string
  type: DocumentType
  status: DocumentStatus
}

export interface Conversation {
  id: string
  workspaceId?: string | null
  title: string
  createdAt: string
  updatedAt: string
  messages: ChatMessage[]
  scope: ScopeDocument[]
}

export interface ConversationSummary {
  id: string
  workspaceId?: string | null
  title: string
  updatedAt: string
}

export interface AskBody {
  conversationId?: string
  workspaceId?: string
  question: string
  /** Trống: phạm vi mặc định (mọi tài liệu của không gian học). */
  documentIds?: string[]
}

export interface StreamMeta {
  conversationId: string
  workspaceId?: string | null
  title: string
  questionId: string
  answerId: string
  documentIds: string[]
}

export interface StreamDone {
  status: MessageStatus
  text: string
  suggestions: string[]
  citations: Citation[]
  elsewhere: { id: string; title: string }[]
  flaggedSources: number[]
}

export interface StreamHandlers {
  onMeta: (meta: StreamMeta) => void
  onChunk: (text: string) => void
  onSafety: (outcome: SafetyOutcome) => void
  onDone: (done: StreamDone) => void
  /** {@code partial}: phần đã hiện trước khi lỗi; {@code rejected}: máy chủ từ chối trước khi có luồng. */
  onError: (code: string, partial: string, rejected: boolean) => void
}

export const QUESTION_MAX = 4000
