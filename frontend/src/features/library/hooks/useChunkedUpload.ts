import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { libraryApi } from '../api/libraryApi'
import {
  ACTIVE_STATES,
  duplicateInfo,
  isRejection,
  runUpload,
  type UploadPatch,
  type UploadState,
  type UploadTransport,
} from '../lib/uploader'
import type { DocumentCard, DuplicateInfo } from '../types'

/** Một tệp trong hàng đợi tải lên. */
export interface UploadItem {
  key: string
  file: File
  /** Thư mục đích (trống: thư mục gốc) và tên của nó lúc kéo thả. */
  folderId: string | null
  folderName: string
  state: UploadState
  sentBytes: number
  uploadId?: string
  document?: DocumentCard
  error?: ApiError
  duplicate?: DuplicateInfo
  /** Thứ tự lần đổi trạng thái đáng báo gần nhất (xong, từ chối, trùng, lỗi, mất mạng), để đọc cho trình đọc màn hình. */
  noticeSeq?: number
}

const NOTICE_STATES: UploadState[] = ['done', 'rejected', 'duplicate', 'failed', 'offline']

/** Số tệp gửi cùng lúc; các tệp còn lại chờ lượt. */
const PARALLEL = 2

let counter = 0
let notices = 0

/**
 * Hàng đợi tải tài liệu lên kho (NCL-05-CN-002): nhận nhiều tệp cùng lúc, gửi từng tệp theo phần với tiến độ riêng, tự
 * tải tiếp khi mạng có lại, giữ lý do từ chối và thông tin trùng để hiển thị. Xong mỗi tệp thì làm mới kho và dung
 * lượng đã dùng.
 */
export function useChunkedUpload(transport: UploadTransport = libraryApi) {
  const client = useQueryClient()
  const [items, setItems] = useState<UploadItem[]>([])
  const itemsRef = useRef<UploadItem[]>([])
  const controllers = useRef(new Map<string, AbortController>())

  const commit = useCallback((update: (current: UploadItem[]) => UploadItem[]) => {
    itemsRef.current = update(itemsRef.current)
    setItems(itemsRef.current)
  }, [])

  const patch = useCallback(
    (key: string, change: Partial<UploadItem>) =>
      commit((current) =>
        current.map((item) => {
          if (item.key !== key) return item
          const noticed = change.state && change.state !== item.state && NOTICE_STATES.includes(change.state)
          return { ...item, ...change, ...(noticed ? { noticeSeq: ++notices } : {}) }
        }),
      ),
    [commit],
  )

  const run = useCallback(
    async (item: UploadItem) => {
      const controller = new AbortController()
      controllers.current.set(item.key, controller)
      try {
        const document = await runUpload({
          file: item.file,
          folderId: item.folderId,
          uploadId: item.uploadId,
          transport,
          signal: controller.signal,
          report: (change: UploadPatch) => patch(item.key, change),
        })
        patch(item.key, { state: 'done', document, sentBytes: item.file.size, error: undefined })
        void client.invalidateQueries({ queryKey: queryKeys.library })
        void client.invalidateQueries({ queryKey: queryKeys.quota })
      } catch (failure) {
        if (controller.signal.aborted) return
        const error = toApiError(failure)
        const duplicate = duplicateInfo(error)
        if (duplicate) {
          patch(item.key, { state: 'duplicate', duplicate, error, uploadId: undefined })
        } else if (isRejection(error)) {
          // Máy chủ đã bỏ lần tải bị từ chối; thử lại phải bắt đầu từ đầu.
          patch(item.key, { state: 'rejected', error, uploadId: undefined })
        } else {
          // Thư mục đích không còn thì lần tải cũng đã bị bỏ.
          patch(item.key, { state: 'failed', error, ...(error.status === 404 ? { uploadId: undefined } : {}) })
        }
      } finally {
        controllers.current.delete(item.key)
      }
    },
    [client, patch, transport],
  )

  // Còn chỗ thì cho tệp đang chờ vào gửi.
  // Đọc danh sách mới nhất (không phải bản của lần hiển thị) để một tệp không bao giờ được cho gửi hai lần.
  useEffect(() => {
    const current = itemsRef.current
    const running = current.filter((item) => item.state !== 'queued' && ACTIVE_STATES.includes(item.state)).length
    const waiting = current.filter((item) => item.state === 'queued').slice(0, Math.max(0, PARALLEL - running))
    for (const item of waiting) {
      patch(item.key, { state: 'preparing' })
      void run({ ...item, state: 'preparing' })
    }
  }, [items, patch, run])

  // Rời trang thì dừng mọi tệp đang gửi; phần đã nhận còn trên máy chủ cho tới khi hết hạn.
  useEffect(() => {
    const active = controllers.current
    return () => active.forEach((controller) => controller.abort())
  }, [])

  const add = useCallback(
    (files: File[], folderId: string | null, folderName: string) => {
      const added = files.map<UploadItem>((file) => ({
        key: `upload-${++counter}`,
        file,
        folderId,
        folderName,
        state: 'queued',
        sentBytes: 0,
      }))
      commit((current) => [...current, ...added])
      return added
    },
    [commit],
  )

  const cancel = useCallback(
    (key: string) => {
      const item = itemsRef.current.find((candidate) => candidate.key === key)
      if (!item || !ACTIVE_STATES.includes(item.state)) return
      controllers.current.get(key)?.abort()
      patch(key, { state: 'cancelled' })
      if (item.uploadId) void libraryApi.cancelUpload(item.uploadId).catch(() => undefined)
    },
    [patch],
  )

  const retry = useCallback(
    (key: string) => {
      const item = itemsRef.current.find((candidate) => candidate.key === key)
      if (!item || item.state !== 'failed') return
      patch(key, { state: 'queued', error: undefined })
    },
    [patch],
  )

  const rename = useCallback(
    (key: string, title: string) => {
      const item = itemsRef.current.find((candidate) => candidate.key === key)
      if (item?.document) patch(key, { document: { ...item.document, title } })
    },
    [patch],
  )

  /** Bỏ khỏi danh sách các tệp đã xong (thành công, bị từ chối, trùng, đã hủy). */
  const clearFinished = useCallback(
    () => commit((current) => current.filter((item) => ACTIVE_STATES.includes(item.state) || item.state === 'failed')),
    [commit],
  )

  return { items, add, cancel, retry, rename, clearFinished }
}
