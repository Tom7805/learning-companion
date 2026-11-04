import { UploadCloud } from 'lucide-react'
import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuotaStatus } from '@/features/quota'
import { cn } from '@/shared/lib/cn'
import { formatBytes } from '@/shared/lib/format'
import { Button } from '@/shared/ui'
import { UPLOAD_ACCEPT } from '../lib/uploadText'

/**
 * Vùng kéo thả nhiều tệp vào kho hoặc vào thư mục đang mở (NCL-05-CN-002), kèm nút chọn tệp cho người không kéo thả
 * được (bàn phím, điện thoại). {@code active}: đang có tệp được kéo qua trang.
 */
export function UploadDropzone({
  targetName,
  active,
  onFiles,
}: {
  targetName: string
  active: boolean
  onFiles: (files: File[]) => void
}) {
  const { t, i18n } = useTranslation()
  const input = useRef<HTMLInputElement>(null)
  const maxFileBytes = useQuotaStatus().data?.storage.maxFileBytes

  return (
    <div
      data-testid="upload-dropzone"
      data-active={active || undefined}
      className={cn(
        'flex flex-col items-start gap-3 rounded-card border-[1.5px] border-dashed border-ink/60 bg-surface p-4',
        'sm:flex-row sm:items-center sm:gap-4',
        active && 'border-solid border-ink bg-sun-soft ring-2 ring-ink',
      )}
    >
      <span aria-hidden="true" className="grid size-11 shrink-0 place-items-center rounded-xl border-[1.5px] border-ink bg-sun">
        <UploadCloud className="size-5" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <p className="text-[0.9375rem] font-medium [overflow-wrap:anywhere]">
          {active ? t('library.upload.dropNow', { folder: targetName }) : t('library.upload.dropHint', { folder: targetName })}
        </p>
        <p className="text-[0.8125rem] leading-snug text-muted">
          {maxFileBytes
            ? t('library.upload.formatsWithLimit', { max: formatBytes(maxFileBytes, i18n.language) })
            : t('library.upload.formats')}
        </p>
      </div>
      <Button
        variant="dark"
        className="w-full sm:w-auto"
        onClick={() => input.current?.click()}
        icon={<UploadCloud className="size-4" aria-hidden="true" />}
      >
        {t('library.upload.choose')}
      </Button>
      <input
        ref={input}
        type="file"
        multiple
        hidden
        accept={UPLOAD_ACCEPT}
        aria-label={t('library.upload.choose')}
        data-testid="upload-input"
        onChange={(event) => {
          const files = Array.from(event.target.files ?? [])
          // Xóa lựa chọn để chọn lại đúng tệp đó (ví dụ sau khi gỡ mật khẩu) vẫn gửi được.
          event.target.value = ''
          if (files.length) onFiles(files)
        }}
      />
    </div>
  )
}
