import { http } from '@/shared/api/httpClient'
import type { DocumentCard, FileLink, LearningDocument } from '../types'

export const libraryApi = {
  async documents() {
    const { data } = await http.get<DocumentCard[]>('/library/documents')
    return data
  },
  async document(id: string) {
    const { data } = await http.get<LearningDocument>(`/library/documents/${encodeURIComponent(id)}`)
    return data
  },
  async fileLink(id: string) {
    const { data } = await http.post<FileLink>(`/library/documents/${encodeURIComponent(id)}/file-link`)
    return data
  },
}
