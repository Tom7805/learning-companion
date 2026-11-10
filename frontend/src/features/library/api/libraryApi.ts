import { http } from '@/shared/api/httpClient'
import { queryParams } from '../lib/query'
import type {
  BulkRequest,
  BulkResult,
  DocumentCard,
  FileLink,
  Folder,
  FolderTrashed,
  FolderTree,
  LearningDocument,
  LibraryQuery,
  NameCheck,
  PasteTextRequest,
  StartUploadRequest,
  DocumentChunk,
  UploadProgress,
  Tag,
  TagColor,
  WebImportRequest,
  ViewerContent,
} from '../types'

const doc = (id: string) => `/library/documents/${encodeURIComponent(id)}`
const folder = (id: string) => `/library/folders/${encodeURIComponent(id)}`
const tag = (id: string) => `/library/tags/${encodeURIComponent(id)}`
const upload = (id: string) => `/library/uploads/${encodeURIComponent(id)}`

export interface PartOptions {
  signal?: AbortSignal
  /** Số byte của phần này đã gửi đi. */
  onProgress?: (loaded: number) => void
}

export const libraryApi = {
  async documents(query?: LibraryQuery) {
    const { data } = await http.get<DocumentCard[]>('/library/documents', {
      params: query ? queryParams(query) : undefined,
      // Tham số lặp lại (?type=PDF&type=PPTX) thay vì type[]=…, đúng cách máy chủ đọc danh sách.
      paramsSerializer: { indexes: null },
    })
    return data
  },
  /** {@code track} false: chỉ xem thông tin, không tính là một lần mở cho lối tắt "Gần đây". */
  async document(id: string, track = true) {
    const { data } = await http.get<LearningDocument>(doc(id), { params: track ? undefined : { track: false } })
    return data
  },
  /** Các đoạn đã lập chỉ mục, kèm vị trí trong tài liệu gốc. */
  async chunks(id: string) {
    const { data } = await http.get<DocumentChunk[]>(`${doc(id)}/chunks`)
    return data
  },
  async viewer(id: string) {
    const { data } = await http.get<ViewerContent>(`${doc(id)}/viewer`)
    return data
  },
  /** Thêm tài liệu từ đường dẫn trang web: máy chủ lấy nội dung chính và chặn địa chỉ nội bộ. */
  async importWeb(body: WebImportRequest) {
    const { data } = await http.post<DocumentCard>('/library/sources/web', body)
    return data
  },
  /** Tạo tài liệu văn bản từ đoạn dán vào kèm tiêu đề. */
  async pasteText(body: PasteTextRequest) {
    const { data } = await http.post<DocumentCard>('/library/sources/text', body)
    return data
  },
  /** Đưa tài liệu xử lý lỗi về hàng chờ để xử lý lại. */
  async reprocess(id: string) {
    const { data } = await http.post<DocumentCard>(`${doc(id)}/reprocess`)
    return data
  },
  async fileLink(id: string) {
    const { data } = await http.post<FileLink>(`${doc(id)}/file-link`)
    return data
  },
  async renameDocument(id: string, title: string) {
    const { data } = await http.patch<DocumentCard>(doc(id), { title })
    return data
  },
  async starDocument(id: string, starred: boolean) {
    const { data } = await http.put<DocumentCard>(`${doc(id)}/star`, { starred })
    return data
  },
  async setDocumentTags(id: string, tagIds: string[]) {
    const { data } = await http.put<DocumentCard>(`${doc(id)}/tags`, { tagIds })
    return data
  },
  async trashDocument(id: string) {
    const { data } = await http.delete<{ trashBatchId: string }>(doc(id))
    return data
  },
  async bulk(request: BulkRequest) {
    const { data } = await http.post<BulkResult>('/library/documents/bulk', request)
    return data
  },
  async folders() {
    const { data } = await http.get<FolderTree>('/library/folders')
    return data
  },
  async checkFolderName(name: string, parentId?: string | null, exceptId?: string) {
    const { data } = await http.get<NameCheck>('/library/folders/name-check', {
      params: { name, parentId: parentId ?? undefined, exceptId },
    })
    return data
  },
  async createFolder(name: string, parentId?: string | null) {
    const { data } = await http.post<Folder>('/library/folders', { name, parentId: parentId ?? null })
    return data
  },
  async renameFolder(id: string, name: string) {
    const { data } = await http.patch<Folder>(folder(id), { name })
    return data
  },
  async moveFolder(id: string, parentId: string | null) {
    const { data } = await http.post<Folder>(`${folder(id)}/move`, { parentId })
    return data
  },
  /** {@code confirmInUse}: đã báo trước các không gian học đang dùng tài liệu trong thư mục và người học vẫn xóa. */
  async trashFolder(id: string, confirmInUse = false) {
    const { data } = await http.delete<FolderTrashed>(folder(id), {
      params: confirmInUse ? { confirmInUse: true } : undefined,
    })
    return data
  },
  async tags() {
    const { data } = await http.get<Tag[]>('/library/tags')
    return data
  },
  async createTag(name: string, color: TagColor) {
    const { data } = await http.post<Tag>('/library/tags', { name, color })
    return data
  },
  async updateTag(id: string, name: string, color: TagColor) {
    const { data } = await http.patch<Tag>(tag(id), { name, color })
    return data
  },
  async deleteTag(id: string) {
    await http.delete(tag(id))
  },
  async startUpload(request: StartUploadRequest, signal?: AbortSignal) {
    const { data } = await http.post<UploadProgress>('/library/uploads', request, { signal })
    return data
  },
  async uploadProgress(id: string, signal?: AbortSignal) {
    const { data } = await http.get<UploadProgress>(upload(id), { signal })
    return data
  },
  async uploadPart(id: string, index: number, part: Blob, options: PartOptions = {}) {
    const { data } = await http.put<UploadProgress>(`${upload(id)}/parts/${index}`, part, {
      headers: { 'Content-Type': 'application/octet-stream' },
      signal: options.signal,
      onUploadProgress: (event) => options.onProgress?.(event.loaded),
      // Một phần có thể lâu trên mạng chậm; đứt kết nối thì giao diện hỏi lại tiến độ và gửi tiếp.
      timeout: 120_000,
    })
    return data
  },
  async completeUpload(id: string, signal?: AbortSignal) {
    const { data } = await http.post<DocumentCard>(`${upload(id)}/complete`, undefined, { signal, timeout: 120_000 })
    return data
  },
  async cancelUpload(id: string) {
    await http.delete(upload(id))
  },
}
