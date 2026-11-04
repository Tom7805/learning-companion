import { ClipboardPaste, Globe } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { documentPath } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { Alert, Button, TextField } from '@/shared/ui'
import { libraryApi } from '../api/libraryApi'
import { duplicateInfo } from '../lib/uploader'
import { normalizeUrl, webImportProblem, type WebImportProblem } from '../lib/webSource'
import type { DocumentCard, DuplicateInfo } from '../types'

/**
 * Dán đường dẫn trang web để thêm vào kho (NCL-05-CN-004 TC-01). Máy chủ lấy phần nội dung chính, chặn địa chỉ thuộc
 * mạng nội bộ (TC-03); trang không lấy được thì nêu lý do và gợi ý dán văn bản thay thế (TC-04).
 */
export function WebImportTab({
  folderId,
  autoFocus = true,
  onAdded,
  onPasteInstead,
}: {
  folderId: string | null
  autoFocus?: boolean
  onAdded: (document: DocumentCard) => void
  onPasteInstead: () => void
}) {
  const { t } = useTranslation()
  const [url, setUrl] = useState('')
  const [touched, setTouched] = useState(false)
  const [busy, setBusy] = useState(false)
  const firstField = useRef<HTMLInputElement>(null)

  // Hộp thoại gốc tự đặt tiêu điểm khi mở (showModal chạy sau khi nội dung dựng xong), nên đặt tiêu điểm ở lượt sau.
  useEffect(() => {
    if (!autoFocus) return
    const timer = window.setTimeout(() => firstField.current?.focus())
    return () => window.clearTimeout(timer)
  }, [autoFocus])
  const [problem, setProblem] = useState<WebImportProblem | null>(null)
  const [duplicate, setDuplicate] = useState<DuplicateInfo | null>(null)

  const normalized = normalizeUrl(url)
  const fieldError = touched && url.trim() && !normalized ? t('library.addSource.web.invalid') : undefined

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (!normalized || busy) return
    setBusy(true)
    setProblem(null)
    setDuplicate(null)
    try {
      onAdded(await libraryApi.importWeb({ url: normalized, folderId }))
    } catch (failure) {
      const error = toApiError(failure)
      const existing = duplicateInfo(error)
      if (existing) setDuplicate(existing)
      else setProblem(webImportProblem(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={submit} noValidate data-testid="web-import-form">
      <TextField
        label={t('library.addSource.web.label')}
        type="url"
        inputMode="url"
        autoComplete="url"
        spellCheck={false}
        value={url}
        onChange={(event) => {
          setUrl(event.target.value)
          setProblem(null)
          setDuplicate(null)
        }}
        onBlur={() => setTouched(true)}
        hint={t('library.addSource.web.hint')}
        error={fieldError}
        ref={firstField}
        data-testid="web-url"
      />
      {problem && <ProblemAlert problem={problem} onPasteInstead={onPasteInstead} />}
      {duplicate && (
        <Alert tone="warning" data-testid="web-duplicate">
          <p>{t('library.addSource.duplicate', { title: duplicate.title })}</p>
          <Link to={documentPath(duplicate.documentId)} className="mt-1 inline-block font-medium underline underline-offset-2">
            {t('library.upload.duplicate.openExisting')}
          </Link>
        </Alert>
      )}
      <p className="text-[0.8125rem] leading-snug text-muted">{t('library.addSource.web.privacy')}</p>
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button
          type="submit"
          variant="accent"
          loading={busy}
          disabled={!url.trim()}
          icon={<Globe className="size-4" aria-hidden="true" />}
          data-testid="web-submit"
        >
          {busy ? t('library.addSource.web.fetching') : t('library.addSource.web.submit')}
        </Button>
      </div>
    </form>
  )
}

function ProblemAlert({ problem, onPasteInstead }: { problem: WebImportProblem; onPasteInstead: () => void }) {
  const { t } = useTranslation()
  const message =
    problem.kind === 'unavailable'
      ? t(`library.addSource.web.unavailable.${problem.reason}`)
      : problem.kind === 'other'
        ? t(`errors.${problem.code}`, { defaultValue: t('library.actionFailed') })
        : t(`library.addSource.web.${problem.kind}`)
  return (
    <Alert tone="danger" role="alert" data-testid="web-problem" data-kind={problem.kind}>
      <p>{message}</p>
      {problem.kind === 'unavailable' && (
        <Button
          variant="outline"
          className="mt-3"
          onClick={onPasteInstead}
          icon={<ClipboardPaste className="size-4" aria-hidden="true" />}
          data-testid="paste-instead"
        >
          {t('library.addSource.web.pasteInstead')}
        </Button>
      )}
    </Alert>
  )
}
