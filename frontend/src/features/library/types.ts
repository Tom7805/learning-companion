export type DocumentType = 'PDF' | 'DOCX' | 'PPTX' | 'TEXT' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'WEB'

/** Một tài liệu trong danh sách thư viện, không kèm nội dung. */
export interface DocumentCard {
  id: string
  title: string
  type: DocumentType
  originalFilename: string
  sizeBytes: number
  createdAt: string
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
