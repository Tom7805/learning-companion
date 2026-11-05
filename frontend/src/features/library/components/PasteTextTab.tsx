import { FileText } from 'lucide-react'
import { useId, useEffect, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { documentPath } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { cn } from '@/shared/lib/cn'
import { Alert, Button, TextField } from '@/shared/ui'
import { libraryApi } from '../api/libraryApi'
import { duplicateInfo } from '../lib/uploader'
import { PASTE_TEXT_MAX, TITLE_MAX } from '../lib/webSource'
import type { DocumentCard, DuplicateInfo } from '../types'

/**
 * Dán văn bản kèm tiêu đề để tạo tài liệu văn bản (NCL-05-CN-004 TC-02): đoạn văn cách nhau bằng dòng trống được chia
 * đoạn đúng thứ tự như tệp văn bản tải lên.
 */
export function PasteTextTab({
  folderId,
  autoFocus = true,
  onAdded,
}: {
  folderId: string | null
  autoFocus?: boolean
  onAdded: (document: DocumentCard) => void
}) {
  const { t, i18n } = useTranslation()
  const textId = useId()
  const [title, setTitle] = useState('')
  const [text, setText] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [busy, setBusy] = useState(false)
  const firstField = useRef<HTMLInputElement>(null)

  // Hộp thoại gốc tự đặt tiêu điểm khi mở (showModal chạy sau khi nội dung dựng xong), nên đặt tiêu điểm ở lượt sau.
  useEffect(() => {
    if (!autoFocus) return
    const timer = window.setTimeout(() => firstField.current?.focus())
    return () => window.clearTimeout(timer)
  }, [autoFocus])
  const [error, setError] = useState<string | null>(null)
  const [duplicate, setDuplicate] = useState<DuplicateInfo | null>(null)

  const titleError = submitted && !title.trim() ? t('library.addSource.text.titleRequired') : undefined
  const textError = submitted && !text.trim() ? t('library.addSource.text.textRequired') : undefined
  const tooLong = text.length > PASTE_TEXT_MAX
  const words = text.trim() ? text.trim().split(/\s+/).length : 0

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitted(true)
    if (!title.trim() || !text.trim() || tooLong || busy) return
    setBusy(true)
    setError(null)
    setDuplicate(null)
    try {
      onAdded(await libraryApi.pasteText({ title: title.trim(), text, folderId }))
    } catch (failure) {
      const apiError = toApiError(failure)
      const existing = duplicateInfo(apiError)
      if (existing) setDuplicate(existing)
      else setError(t(`errors.${apiError.code}`, { defaultValue: t('library.actionFailed') }))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={submit} noValidate data-testid="paste-text-form">
      <TextField
        label={t('library.addSource.text.titleLabel')}
        value={title}
        maxLength={TITLE_MAX}
        onChange={(event) => setTitle(event.target.value)}
        error={titleError}
        ref={firstField}
        data-testid="paste-title"
      />
      <div className="flex flex-col gap-1.5">
        <label htmlFor={textId} className="text-sm font-medium">
          {t('library.addSource.text.textLabel')}
        </label>
        <textarea
          id={textId}
          value={text}
          onChange={(event) => {
            setText(event.target.value)
            setDuplicate(null)
          }}
          rows={9}
          placeholder={t('library.addSource.text.placeholder')}
          aria-invalid={textError || tooLong ? true : undefined}
          aria-describedby={`${textId}-count${textError ? ` ${textId}-error` : ''}`}
          className={cn(
            'min-h-44 w-full resize-y rounded-field border-[1.5px] border-ink bg-surface px-4 py-3 text-[0.9375rem] leading-relaxed',
            'outline-none placeholder:text-subtle focus:border-focus focus:ring-4 focus:ring-focus/15',
            (textError || tooLong) && 'border-danger focus:border-danger focus:ring-danger/15',
          )}
          data-testid="paste-text"
        />
        <div className="flex flex-wrap items-center justify-between gap-2 text-[0.8125rem]">
          {textError ? (
            <span id={`${textId}-error`} className="text-danger">
              {textError}
            </span>
          ) : (
            <span className="text-muted">{t('library.addSource.text.hint')}</span>
          )}
          <span id={`${textId}-count`} className={cn('tabular-nums', tooLong ? 'text-danger' : 'text-muted')} data-testid="paste-count">
            {tooLong
              ? t('library.addSource.text.tooLong', { max: PASTE_TEXT_MAX.toLocaleString(i18n.language) })
              : t('library.addSource.text.count', { count: words, words: words.toLocaleString(i18n.language), chars: text.length.toLocaleString(i18n.language) })}
          </span>
        </div>
      </div>
      {error && (
        <Alert tone="danger" role="alert" data-testid="paste-error">
          {error}
        </Alert>
      )}
      {duplicate && (
        <Alert tone="warning" data-testid="paste-duplicate">
          <p>{t('library.addSource.duplicate', { title: duplicate.title })}</p>
          <Link to={documentPath(duplicate.documentId)} className="mt-1 inline-block font-medium underline underline-offset-2">
            {t('library.upload.duplicate.openExisting')}
          </Link>
        </Alert>
      )}
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button type="submit" variant="accent" loading={busy} icon={<FileText className="size-4" aria-hidden="true" />} data-testid="paste-submit">
          {t('library.addSource.text.submit')}
        </Button>
      </div>
    </form>
  )
}
