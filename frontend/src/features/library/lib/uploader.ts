import { detailNumber, toApiError, type ApiError } from '@/shared/api/errors'
import type { PartOptions } from '../api/libraryApi'
import type { DocumentCard, DuplicateInfo, StartUploadRequest, UploadProgress } from '../types'

/** Các bước của một tệp trong hàng đợi tải lên. */
export type UploadState =
  | 'queued'
  | 'preparing'
  | 'uploading'
  | 'offline'
  | 'verifying'
  | 'done'
  | 'rejected'
  | 'duplicate'
  | 'failed'
  | 'cancelled'

export const ACTIVE_STATES: UploadState[] = ['queued', 'preparing', 'uploading', 'offline', 'verifying']

/** Phần máy chủ mà luồng tải cần; tách ra để kiểm thử không cần mạng thật. */
export interface UploadTransport {
  startUpload(request: StartUploadRequest, signal?: AbortSignal): Promise<UploadProgress>
  uploadProgress(id: string, signal?: AbortSignal): Promise<UploadProgress>
  uploadPart(id: string, index: number, part: Blob, options?: PartOptions): Promise<UploadProgress>
  completeUpload(id: string, signal?: AbortSignal): Promise<DocumentCard>
}

export interface UploadPatch {
  state?: UploadState
  sentBytes?: number
  uploadId?: string
}

export interface RunOptions {
  file: File
  folderId: string | null
  /** Lần tải đã bắt đầu trước đó (thử lại): hỏi tiến độ và gửi tiếp thay vì bắt đầu lại từ đầu. */
  uploadId?: string
  transport: UploadTransport
  signal: AbortSignal
  report: (patch: UploadPatch) => void
  /** Chờ mạng có lại trước lần thử thứ {@code attempt}; mặc định chờ sự kiện online rồi lùi dần. */
  waitForNetwork?: (attempt: number, signal: AbortSignal) => Promise<void>
}

/** Dấu vân tay chỉ tính cho tệp vừa phải để không giữ cả tệp rất lớn trong bộ nhớ. */
const FINGERPRINT_LIMIT = 64 * 1024 * 1024

/** SHA-256 dạng hex của tệp để máy chủ báo trùng trước khi gửi; không tính được thì bỏ qua, máy chủ tự tính sau. */
export async function fingerprint(file: Blob): Promise<string | undefined> {
  const subtle = globalThis.crypto?.subtle
  if (!subtle || file.size > FINGERPRINT_LIMIT || typeof file.arrayBuffer !== 'function') return undefined
  try {
    const digest = await subtle.digest('SHA-256', await file.arrayBuffer())
    return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
  } catch {
    return undefined
  }
}

export function isNetworkError(error: ApiError) {
  // Mất mạng, hết thời gian chờ, hoặc máy chủ tạm không trả lời (cổng, cân bằng tải) đều là lỗi đường truyền.
  return error.code === 'NETWORK_ERROR' || error.status === 502 || error.status === 504
}

/** Chờ trình duyệt báo có mạng, rồi chờ thêm một quãng tăng dần (tối đa 15 giây) trước khi thử lại. */
export function defaultWaitForNetwork(attempt: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const delay = Math.min(1000 * 2 ** Math.min(attempt, 4), 15_000)
    let timer: ReturnType<typeof setTimeout> | undefined
    const done = () => {
      window.removeEventListener('online', onOnline)
      signal.removeEventListener('abort', onAbort)
      resolve()
    }
    const onOnline = () => {
      window.removeEventListener('online', onOnline)
      timer = setTimeout(done, 500)
    }
    const onAbort = () => {
      if (timer) clearTimeout(timer)
      window.removeEventListener('online', onOnline)
      reject(new DOMException('Đã hủy', 'AbortError'))
    }
    signal.addEventListener('abort', onAbort, { once: true })
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      window.addEventListener('online', onOnline)
    } else {
      timer = setTimeout(done, delay)
    }
  })
}

/**
 * Tải trọn một tệp theo từng phần (NCL-05-CN-002): bắt đầu (kèm dấu vân tay để báo trùng sớm), gửi lần lượt các phần,
 * rồi hoàn tất. Mất kết nối giữa chừng thì chờ mạng có lại, hỏi máy chủ đã nhận tới đâu và gửi tiếp từ phần còn thiếu
 * (TC-04). Lỗi không phải do đường truyền (bị từ chối, trùng, hết chỗ) được ném ra cho hàng đợi hiển thị.
 */
export async function runUpload({
  file,
  folderId,
  uploadId,
  transport,
  signal,
  report,
  waitForNetwork = defaultWaitForNetwork,
}: RunOptions): Promise<DocumentCard> {
  let attempt = 0
  const retrying = async <T>(action: () => Promise<T>, resumeState: UploadState): Promise<T> => {
    for (;;) {
      try {
        const result = await action()
        attempt = 0
        return result
      } catch (error) {
        if (signal.aborted || !isNetworkError(toApiError(error))) throw error
        report({ state: 'offline' })
        await waitForNetwork(attempt++, signal)
        report({ state: resumeState })
      }
    }
  }

  let progress: UploadProgress
  if (uploadId) {
    report({ state: 'uploading' })
    progress = await retrying(() => transport.uploadProgress(uploadId, signal), 'uploading')
  } else {
    report({ state: 'preparing' })
    const sha256 = await fingerprint(file)
    progress = await retrying(
      () => transport.startUpload({ fileName: file.name, sizeBytes: file.size, folderId, sha256 }, signal),
      'preparing',
    )
    report({ uploadId: progress.id, state: 'uploading' })
  }
  const id = progress.id
  const { chunkSize, partCount } = progress
  let next = progress.nextPart
  report({ sentBytes: progress.receivedBytes })

  while (next < partCount && progress.receivedBytes < file.size && progress.status !== 'COMPLETED') {
    const from = next * chunkSize
    const part = file.slice(from, Math.min(file.size, from + chunkSize))
    try {
      progress = await transport.uploadPart(id, next, part, {
        signal,
        onProgress: (loaded) => report({ sentBytes: from + Math.min(loaded, part.size) }),
      })
      attempt = 0
      next = progress.nextPart
      report({ sentBytes: progress.receivedBytes })
    } catch (error) {
      const apiError = toApiError(error)
      if (signal.aborted) throw error
      if (apiError.code === 'UPLOAD_PART_OUT_OF_ORDER') {
        next = detailNumber(apiError, 'nextPart') ?? next
        continue
      }
      if (!isNetworkError(apiError)) throw error
      // Mạng đứt khi đang gửi: chờ mạng có lại rồi hỏi máy chủ đã nhận tới phần nào.
      report({ state: 'offline' })
      await waitForNetwork(attempt++, signal)
      report({ state: 'uploading' })
      progress = await retrying(() => transport.uploadProgress(id, signal), 'uploading')
      next = progress.nextPart
      report({ sentBytes: progress.receivedBytes })
    }
  }

  report({ state: 'verifying', sentBytes: file.size })
  return retrying(() => transport.completeUpload(id, signal), 'verifying')
}

/** Đọc chi tiết báo trùng từ lỗi máy chủ. */
export function duplicateInfo(error: ApiError): DuplicateInfo | undefined {
  if (error.code !== 'LIBRARY_DOCUMENT_DUPLICATE' || !error.details) return undefined
  const { documentId, title, folderId, folderPath } = error.details
  if (typeof documentId !== 'string') return undefined
  return {
    documentId,
    title: typeof title === 'string' ? title : '',
    folderId: typeof folderId === 'string' ? folderId : null,
    folderPath: Array.isArray(folderPath) ? folderPath.filter((name): name is string => typeof name === 'string') : [],
  }
}

/** Lỗi khiến tệp không được nhận (khác lỗi tạm thời có thể thử lại). */
export function isRejection(error: ApiError) {
  return ['UPLOAD_REJECTED', 'FILE_TOO_LARGE', 'STORAGE_QUOTA_EXCEEDED', 'VALIDATION_FAILED'].includes(error.code)
}
