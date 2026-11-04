export type DocumentType = 'PDF' | 'DOCX' | 'PPTX' | 'EPUB' | 'TEXT' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'WEB'

export const DOCUMENT_TYPES: DocumentType[] = ['PDF', 'DOCX', 'PPTX', 'EPUB', 'TEXT', 'IMAGE', 'AUDIO', 'VIDEO', 'WEB']

/** Trạng thái xử lý (trích văn bản, nhận dạng chữ, lập chỉ mục) của tài liệu. */
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export const DOCUMENT_STATUSES: DocumentStatus[] = ['PENDING', 'PROCESSING', 'READY', 'FAILED']

/** Lý do tài liệu xử lý lỗi (NCL-05-CN-003). */
export type ProcessingError = 'NO_TEXT' | 'OCR_FAILED' | 'INDEXING_FAILED' | 'UNREADABLE' | 'UNSUPPORTED' | 'INTERNAL'

/** Kết quả trích văn bản, nhận dạng chữ và lập chỉ mục của tài liệu. */
export interface ProcessingInfo {
  error?: ProcessingError | null
  /** Trang lỗi khi lỗi nằm ở một trang (ví dụ nhận dạng chữ lỗi ở trang 12). */
  errorPage?: number | null
  /** Lỗi tạm thời: bấm thử lại có thể được. Lỗi do chính tệp thì cần tải bản khác. */
  retryable: boolean
  /** Số trang, số trang trình chiếu, số ảnh hoặc số đoạn văn. */
  units?: number | null
  chunks: number
  processedAt?: string | null
}

/** Vị trí của một đoạn trong tài liệu gốc. */
export interface ChunkLocation {
  kind: 'PAGE' | 'SLIDE' | 'IMAGE' | 'PARAGRAPH'
  number: number
  end: number
}

/** Một đoạn đã lập chỉ mục của tài liệu. */
export interface DocumentChunk {
  ordinal: number
  location: ChunkLocation
  heading?: string | null
  content: string
  source: 'TEXT' | 'OCR'
}

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
  processing?: ProcessingInfo
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

/** Tiến độ một lần tải tệp lên kho (NCL-05-CN-002); gửi tiếp từ phần {@code nextPart}. */
export interface UploadProgress {
  id: string
  fileName: string
  folderId?: string | null
  sizeBytes: number
  chunkSize: number
  partCount: number
  receivedBytes: number
  nextPart: number
  status: 'UPLOADING' | 'COMPLETED'
  documentId?: string | null
  expiresAt: string
}

export interface StartUploadRequest {
  fileName: string
  sizeBytes: number
  folderId?: string | null
  /** Dấu vân tay SHA-256 tự tính để máy chủ báo trùng ngay, khỏi gửi cả tệp. */
  sha256?: string
}

/** Chi tiết lỗi LIBRARY_DOCUMENT_DUPLICATE: bản đã có cùng nội dung và vị trí của nó. */
export interface DuplicateInfo {
  documentId: string
  title: string
  folderId?: string | null
  /** Tên các thư mục từ gốc tới thư mục chứa bản đã có; rỗng là thư mục gốc. */
  folderPath: string[]
}

export interface FolderTrashed {
  batchId: string
  folders: number
  documents: number
}
