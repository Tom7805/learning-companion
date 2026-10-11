import { useCallback, useRef, useState } from 'react'
import { libraryApi } from '../api/libraryApi'
import type { FileLink } from '../types'

/** Còn ít hơn chừng này thì xin đường dẫn mới luôn, khỏi gửi một yêu cầu chắc chắn bị từ chối. */
const RENEW_BEFORE_MS = 10_000

export class FileOpenError extends Error {
  constructor(readonly status: number) {
    super(`Không mở được tệp (${status})`)
  }
}

/**
 * Mở tệp gốc qua đường dẫn ký có hạn năm phút (NCL-01-CN-004). Đồng hồ máy người học có thể lệch với máy chủ,
 * nên ngoài việc tự đổi đường dẫn sắp hết hạn, khi kho tệp vẫn báo hết hạn (410) thì tự xin đường dẫn mới
 * và mở lại đúng một lần.
 */
export function useOriginalFile(documentId: string) {
  const link = useRef<FileLink | null>(null)
  const [renewals, setRenewals] = useState(0)

  const freshLink = useCallback(async () => {
    link.current = await libraryApi.fileLink(documentId)
    return link.current
  }, [documentId])

  const open = useCallback(async (download = false) => {
    const cached = link.current
    const usable = cached && Date.parse(cached.expiresAt) - Date.now() > RENEW_BEFORE_MS ? cached : await freshLink()
    const withMode = (url: string) => {
      const absolute = new URL(url, window.location.origin)
      if (download) absolute.searchParams.set('download', 'true')
      return absolute.toString()
    }

    let response = await fetch(withMode(usable.url), { credentials: 'same-origin' })
    if (response.status === 410) {
      response = await fetch(withMode((await freshLink()).url), { credentials: 'same-origin' })
      setRenewals((count) => count + 1)
    }
    if (!response.ok) {
      throw new FileOpenError(response.status)
    }
    return response.blob()
  }, [freshLink])

  return { open, renewals }
}
