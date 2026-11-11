import type { Folder } from '@/features/library/types'
import { http } from '@/shared/api/httpClient'
import type {
  AttachedDocument,
  AttachResult,
  TopicInput,
  TopicTree,
  UsageTarget,
  Workspace,
  WorkspaceInput,
  WorkspaceSummary,
} from '../types'

const workspace = (id: string) => `/workspaces/${encodeURIComponent(id)}`

export const workspacesApi = {
  async list() {
    const { data } = await http.get<Workspace[]>('/workspaces')
    return data
  },
  /** Mở không gian học; máy chủ ghi lại lần mở để thanh bên đưa nó lên đầu. */
  async get(id: string) {
    const { data } = await http.get<Workspace>(workspace(id))
    return data
  },
  async create(input: WorkspaceInput) {
    const { data } = await http.post<Workspace>('/workspaces', input)
    return data
  },
  async update(id: string, input: WorkspaceInput) {
    const { data } = await http.patch<Workspace>(workspace(id), input)
    return data
  },
  /** Đưa vào thùng rác; tài liệu đã gắn vẫn nguyên trong kho. */
  async trash(id: string) {
    const { data } = await http.delete<{ trashBatchId: string }>(workspace(id))
    return data
  },
  async documents(id: string) {
    const { data } = await http.get<AttachedDocument[]>(`${workspace(id)}/documents`)
    return data
  },
  async attach(id: string, documentIds: string[]) {
    const { data } = await http.post<AttachResult>(`${workspace(id)}/documents`, { documentIds })
    return data
  },
  /** Gỡ khỏi không gian học: chỉ bỏ liên kết, tài liệu vẫn ở kho. */
  async detach(id: string, documentId: string) {
    await http.delete(`${workspace(id)}/documents/${encodeURIComponent(documentId)}`)
  },
  /** Thư mục trong kho nhận tệp tải lên ngay tại không gian học; chưa có thì máy chủ tạo. */
  async defaultFolder(id: string) {
    const { data } = await http.post<Folder>(`${workspace(id)}/default-folder`)
    return data
  },
  /** Các không gian học đang dùng những tài liệu này, hoặc tài liệu nằm trong thư mục này. */
  async usage(target: UsageTarget) {
    const { data } = await http.get<WorkspaceSummary[]>('/workspaces/usage', {
      params: { documentId: target.documentIds, folderId: target.folderId },
      paramsSerializer: { indexes: null },
    })
    return data
  },
  /** Dàn ý chủ đề của không gian học (NCL-06-CN-002); mọi thao tác trả lại cả cây. */
  async topics(id: string) {
    const { data } = await http.get<TopicTree>(`${workspace(id)}/topics`)
    return data
  },
  async addTopic(id: string, input: TopicInput) {
    const { data } = await http.post<TopicTree>(`${workspace(id)}/topics`, input)
    return data
  },
  /** Trợ lý đề xuất dàn ý từ các nguồn sẵn sàng; tốn lượt. */
  async suggestTopics(id: string) {
    const { data } = await http.post<TopicTree>(`${workspace(id)}/topics/suggest`, undefined, { timeout: 120_000 })
    return data
  },
  async updateTopic(id: string, topicId: string, input: { title?: string; goal?: string }) {
    const { data } = await http.patch<TopicTree>(`${workspace(id)}/topics/${encodeURIComponent(topicId)}`, input)
    return data
  },
  async moveTopic(id: string, topicId: string, parentId: string | null, index: number) {
    const { data } = await http.put<TopicTree>(`${workspace(id)}/topics/${encodeURIComponent(topicId)}/position`, {
      parentId,
      index,
    })
    return data
  },
  async mergeTopic(id: string, topicId: string, intoId: string) {
    const { data } = await http.post<TopicTree>(`${workspace(id)}/topics/${encodeURIComponent(topicId)}/merge`, { intoId })
    return data
  },
  async splitTopic(id: string, topicId: string, title: string, childIds: string[]) {
    const { data } = await http.post<TopicTree>(`${workspace(id)}/topics/${encodeURIComponent(topicId)}/split`, {
      title,
      childIds,
    })
    return data
  },
  async deleteTopic(id: string, topicId: string) {
    const { data } = await http.delete<TopicTree>(`${workspace(id)}/topics/${encodeURIComponent(topicId)}`)
    return data
  },
}
