import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useChunkedUpload } from '@/features/library/hooks/useChunkedUpload'
import type { Folder } from '@/features/library/types'
import { queryKeys } from '@/shared/api/queryKeys'
import { workspacesApi } from '../api/workspacesApi'

/** Kết quả gắn một tệp vừa tải: gắn tài liệu mới, dùng lại bản đã có trong kho, hoặc chưa gắn được. */
export type UploadLink = 'attached' | 'reused' | 'failed'

/**
 * Tải tệp mới ngay tại không gian học (NCL-06-CN-001): tệp vào thư mục mặc định của không gian học trong kho rồi được
 * gắn luôn. Tệp trùng nội dung với tài liệu đã có trong kho thì không lưu thêm mà gắn bản có sẵn (QTN-17).
 */
export function useWorkspaceUploads(workspaceId: string) {
  const client = useQueryClient()
  const uploads = useChunkedUpload()
  const [folder, setFolder] = useState<Pick<Folder, 'id' | 'name'> | null>(null)
  const [preparing, setPreparing] = useState(false)
  const [folderFailed, setFolderFailed] = useState(false)
  const [links, setLinks] = useState<Record<string, UploadLink>>({})
  const handled = useRef(new Set<string>())
  const { add, items } = uploads

  const start = useCallback(
    async (files: File[]) => {
      if (!files.length) return 0
      setFolderFailed(false)
      let target = folder
      if (!target) {
        setPreparing(true)
        try {
          target = await workspacesApi.defaultFolder(workspaceId)
          setFolder(target)
          void client.invalidateQueries({ queryKey: queryKeys.folderTree })
        } catch {
          setFolderFailed(true)
          return 0
        } finally {
          setPreparing(false)
        }
      }
      return add(files, target.id, target.name).length
    },
    [add, client, folder, workspaceId],
  )

  // Mỗi tệp tải xong (hoặc trùng với bản đã có) được gắn đúng một lần.
  useEffect(() => {
    for (const item of items) {
      const documentId =
        item.state === 'done' ? item.document?.id : item.state === 'duplicate' ? item.duplicate?.documentId : undefined
      if (!documentId || handled.current.has(item.key)) continue
      handled.current.add(item.key)
      const reused = item.state === 'duplicate'
      workspacesApi
        .attach(workspaceId, [documentId])
        .then(() => setLinks((current) => ({ ...current, [item.key]: reused ? 'reused' : 'attached' })))
        .catch(() => setLinks((current) => ({ ...current, [item.key]: 'failed' })))
        .finally(() => void client.invalidateQueries({ queryKey: queryKeys.workspaces }))
    }
  }, [items, workspaceId, client])

  return { ...uploads, start, folder, preparing, folderFailed, links }
}

export type WorkspaceUploads = ReturnType<typeof useWorkspaceUploads>
