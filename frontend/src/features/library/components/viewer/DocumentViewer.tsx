import { Download, Eye, FileLock2, RotateCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError } from '@/shared/api/errors'
import { saveBlob } from '@/shared/lib/download'
import { formatBytes } from '@/shared/lib/format'
import { Alert, Button, Spinner } from '@/shared/ui'
import { FileOpenError, useOriginalFile } from '../../hooks/useOriginalFile'
import type { LearningDocument } from '../../types'

type Preview =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'text'; text: string }
  | { kind: 'embed'; url: string; mime: string }
  | { kind: 'unsupported' }

/**
 * Tệp gốc: xem trước ngay trong trang hoặc tải xuống. Tệp được tải qua đường dẫn ký vào bộ nhớ trình duyệt,
 * không mở thẻ mới, nên đường dẫn không nằm lại trong lịch sử trình duyệt.
 */
export function DocumentViewer({ document }: { document: LearningDocument }) {
  const { t, i18n } = useTranslation()
  const { open, renewals } = useOriginalFile(document.id)
  const [preview, setPreview] = useState<Preview>({ kind: 'idle' })
  const [busy, setBusy] = useState<'preview' | 'download' | null>(null)
  const [errorKey, setErrorKey] = useState<string | null>(null)
  const embedUrl = preview.kind === 'embed' ? preview.url : null

  useEffect(() => {
    if (!embedUrl) return undefined
    return () => URL.revokeObjectURL(embedUrl)
  }, [embedUrl])

  const run = async (mode: 'preview' | 'download') => {
    setBusy(mode)
    setErrorKey(null)
    if (mode === 'preview') setPreview({ kind: 'loading' })
    try {
      const blob = await open(mode === 'download')
      if (mode === 'download') {
        saveBlob(blob, document.originalFilename)
      } else {
        setPreview(await toPreview(blob, document.mimeType))
      }
    } catch (error) {
      if (mode === 'preview') setPreview({ kind: 'idle' })
      setErrorKey(error instanceof FileOpenError ? 'library.file.openFailed' : `errors.${toApiError(error).code}`)
    } finally {
      setBusy(null)
    }
  }

  const shown = preview.kind === 'text' || preview.kind === 'embed' || preview.kind === 'unsupported'

  return (
    <section
      aria-labelledby="original-file-title"
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-sky-soft p-5 sm:p-6"
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-start gap-3">
          <span
            aria-hidden="true"
            className="grid size-11 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-sky"
          >
            <FileLock2 className="size-5" />
          </span>
          <div className="min-w-0">
            <h2 id="original-file-title" className="text-lg font-medium">
              {t('library.file.title')}
            </h2>
            <p className="truncate text-sm text-ink-soft" data-testid="original-filename">
              {document.originalFilename} · {formatBytes(document.sizeBytes, i18n.language)}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="dark"
            loading={busy === 'preview'}
            disabled={busy !== null}
            onClick={() => void run('preview')}
            icon={shown ? <RotateCw className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
            data-testid="preview-file"
          >
            {shown ? t('library.file.reload') : t('library.file.preview')}
          </Button>
          <Button
            variant="outline"
            loading={busy === 'download'}
            disabled={busy !== null}
            onClick={() => void run('download')}
            icon={<Download className="size-4" aria-hidden="true" />}
          >
            {t('library.file.download')}
          </Button>
        </div>
      </div>

      <p className="text-[0.8125rem] leading-relaxed text-ink-soft">{t('library.file.protected')}</p>

      {renewals > 0 && (
        <Alert tone="success" key={renewals} data-testid="link-renewed">
          {t('library.file.renewed')}
        </Alert>
      )}
      {errorKey && (
        <Alert tone="danger" data-testid="file-error">
          {t(errorKey, { defaultValue: t('errors.INTERNAL_ERROR') })}
        </Alert>
      )}

      {preview.kind === 'loading' && (
        <div className="grid h-40 place-items-center rounded-field border-[1.5px] border-dashed border-ink/40 bg-surface">
          <Spinner className="size-6" label={t('library.file.loading')} />
        </div>
      )}
      {preview.kind === 'text' && (
        <pre
          data-testid="file-preview"
          tabIndex={0}
          aria-label={t('library.file.previewLabel', { name: document.originalFilename })}
          className="max-h-96 overflow-auto whitespace-pre-wrap rounded-field border-[1.5px] border-ink bg-surface p-4 font-sans text-sm leading-relaxed"
        >
          {preview.text}
        </pre>
      )}
      {preview.kind === 'embed' &&
        (preview.mime.startsWith('image/') ? (
          <img
            src={preview.url}
            alt={document.title}
            data-testid="file-preview"
            className="max-h-[32rem] w-full rounded-field border-[1.5px] border-ink bg-surface object-contain"
          />
        ) : (
          <iframe
            src={preview.url}
            title={t('library.file.previewLabel', { name: document.originalFilename })}
            data-testid="file-preview"
            className="h-[32rem] w-full rounded-field border-[1.5px] border-ink bg-surface"
          />
        ))}
      {preview.kind === 'unsupported' && <Alert tone="info">{t('library.file.unsupported')}</Alert>}
    </section>
  )
}

async function toPreview(blob: Blob, mime: string): Promise<Preview> {
  if (mime.startsWith('text/')) return { kind: 'text', text: await blob.text() }
  if (mime === 'application/pdf' || mime.startsWith('image/')) {
    return { kind: 'embed', url: URL.createObjectURL(blob), mime }
  }
  return { kind: 'unsupported' }
}
