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
  Tag,
  TagColor,
} from '../types'

const doc = (id: string) => `/library/documents/${encodeURIComponent(id)}`
const folder = (id: string) => `/library/folders/${encodeURIComponent(id)}`
const tag = (id: string) => `/library/tags/${encodeURIComponent(id)}`

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
  async trashFolder(id: string) {
    const { data } = await http.delete<FolderTrashed>(folder(id))
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
}
