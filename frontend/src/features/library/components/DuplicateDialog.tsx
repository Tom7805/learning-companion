import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { documentPath, routes } from '@/app/routes'
import { Button, Dialog, buttonClasses } from '@/shared/ui'
import { duplicateLocation } from '../lib/uploadText'
import type { DuplicateInfo } from '../types'

/**
 * Báo tệp vừa chọn trùng nội dung với một tài liệu đã có trong kho (NCL-05-CN-002 TC-03, QTN-17): nêu vị trí của bản
 * đã có và gợi ý dùng lại bản đó thay vì lưu thêm.
 */
export function DuplicateDialog({
  fileName,
  info,
  onClose,
}: {
  fileName: string
  info: DuplicateInfo | null
  onClose: () => void
}) {
  const { t } = useTranslation()
  const location = info ? duplicateLocation(info, t('library.shortcuts.root')) : ''
  const folderHref = info?.folderId
    ? `${routes.library}?folder=${encodeURIComponent(info.folderId)}`
    : `${routes.library}?view=root`
  return (
    <Dialog
      open={info !== null}
      onClose={onClose}
      title={t('library.upload.duplicate.title')}
      description={info ? t('library.upload.duplicate.body', { file: fileName, title: info.title, location }) : undefined}
      footer={
        info && (
          <>
            <Button variant="outline" onClick={onClose}>
              {t('common.close')}
            </Button>
            <Link to={folderHref} className={buttonClasses('outline')} onClick={onClose}>
              {t('library.upload.duplicate.openFolder')}
            </Link>
            <Link to={documentPath(info.documentId)} className={buttonClasses('accent')} onClick={onClose} data-testid="open-existing">
              {t('library.upload.duplicate.openExisting')}
            </Link>
          </>
        )
      }
    >
      {info && (
        <p className="rounded-xl border-[1.5px] border-ink bg-sky-soft px-4 py-3 text-sm leading-relaxed" data-testid="duplicate-location">
          {t('library.upload.duplicate.reuse')}
        </p>
      )}
    </Dialog>
  )
}
