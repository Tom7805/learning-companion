export type DocumentType = 'PDF' | 'DOCX' | 'PPTX' | 'TEXT' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'WEB'

export const DOCUMENT_TYPES: DocumentType[] = ['PDF', 'DOCX', 'PPTX', 'TEXT', 'IMAGE', 'AUDIO', 'VIDEO', 'WEB']

/** Trạng thái xử lý (trích văn bản, nhận dạng chữ, lập chỉ mục) của tài liệu. */
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export const DOCUMENT_STATUSES: DocumentStatus[] = ['PENDING', 'PROCESSING', 'READY', 'FAILED']

/** Một tài liệu trong danh sách kho tài liệu, không kèm nội dung. */
export interface DocumentCard {
  id: string
  title: string
  type: DocumentType
  originalFilename: string
  mimeType?: string
  sizeBytes: number
  createdAt: string
  /** Trống: tài liệu nằm ở thư mục gốc. */
  folderId?: string | null
  tagIds?: string[]
  starred?: boolean
  status?: DocumentStatus
  lastOpenedAt?: string | null
}

/** Chi tiết tài liệu kèm toàn bộ nội dung, chỉ chủ sở hữu nhận được. */
export interface LearningDocument extends DocumentCard {
  contentText: string
  mimeType: string
  updatedAt: string
}

/** Đường dẫn ký để mở tệp gốc, hết hạn sau năm phút. */
export interface FileLink {
  url: string
  expiresAt: string
}

export interface Folder {
  id: string
  /** Trống: thư mục nằm ngay ở gốc kho. */
  parentId?: string | null
  name: string
  /** Số tài liệu nằm trực tiếp trong thư mục này. */
  documentCount: number
  createdAt: string
}

export interface FolderTree {
  folders: Folder[]
  rootDocumentCount: number
  totalDocumentCount: number
  starredCount: number
}

export interface NameCheck {
  available: boolean
  suggestion?: string | null
}

export type TagColor = 'SUN' | 'LILAC' | 'SKY' | 'MINT' | 'CORAL' | 'INK'

export const TAG_COLORS: TagColor[] = ['SUN', 'LILAC', 'SKY', 'MINT', 'CORAL', 'INK']

export interface Tag {
  id: string
  name: string
  color: TagColor
  documentCount: number
}

/** Phạm vi đang xem: cả kho, một thư mục (trống: thư mục gốc), gần đây hoặc có gắn sao. */
export type LibraryScope = 'ALL' | 'FOLDER' | 'RECENT' | 'STARRED'
export type LibrarySort = 'NAME' | 'ADDED' | 'OPENED' | 'SIZE'
export type SortDirection = 'ASC' | 'DESC'

export interface LibraryQuery {
  scope: LibraryScope
  folderId?: string | null
  types: DocumentType[]
  tagIds: string[]
  statuses: DocumentStatus[]
  /** Ngày thêm, dạng yyyy-mm-dd theo giờ địa phương, tính cả hai đầu. */
  from?: string
  to?: string
  sort: LibrarySort
  direction: SortDirection
}

export type BulkAction = 'MOVE' | 'ADD_TAGS' | 'REMOVE_TAGS' | 'STAR' | 'UNSTAR' | 'TRASH'

export interface BulkRequest {
  action: BulkAction
  documentIds: string[]
  folderId?: string | null
  tagIds?: string[]
}

export interface BulkResult {
  action: BulkAction
  affected: number
  trashBatchId?: string | null
}

export interface FolderTrashed {
  batchId: string
  folders: number
  documents: number
}
