import { ArrowUp, Square } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { QUESTION_MAX } from '../types'

/**
 * Ô nhập câu hỏi lớn ở cuối màn hình hội thoại: Enter để gửi, Shift Enter để xuống dòng; khi trợ lý đang trả lời, nút
 * gửi thành nút dừng. {@code toolbar} chứa các nút đính kèm, chọn nguồn.
 */
export function ChatComposer({
  busy,
  canStop,
  disabled,
  autoFocus,
  draft,
  toolbar,
  onSend,
  onStop,
}: {
  busy: boolean
  canStop: boolean
  disabled?: boolean
  autoFocus?: boolean
  /** Đặt sẵn nội dung ô nhập, ví dụ khi bấm một câu hỏi gợi ý; {@code key} đổi thì đặt lại. */
  draft?: { key: number; text: string }
  toolbar?: ReactNode
  onSend: (question: string) => void
  onStop: () => void
}) {
  const { t, i18n } = useTranslation()
  const [text, setText] = useState('')
  const field = useRef<HTMLTextAreaElement>(null)
  const id = useId()
  const [draftKey, setDraftKey] = useState(draft?.key)
  if (draft && draft.key !== draftKey) {
    setDraftKey(draft.key)
    setText(draft.text)
  }
  const trimmed = text.trim()
  const tooLong = trimmed.length > QUESTION_MAX

  useEffect(() => {
    if (!autoFocus) return
    const timer = window.setTimeout(() => field.current?.focus(), 0)
    return () => window.clearTimeout(timer)
  }, [autoFocus])

  // Ô nhập cao dần theo nội dung, tối đa khoảng tám dòng.
  useEffect(() => {
    const element = field.current
    if (!element) return
    element.style.height = 'auto'
    element.style.height = `${Math.min(element.scrollHeight, 240)}px`
  }, [text])

  const submit = (event?: FormEvent) => {
    event?.preventDefault()
    if (busy || disabled || !trimmed || tooLong) return
    onSend(trimmed)
    setText('')
  }

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <form
      onSubmit={submit}
      data-testid="chat-composer"
      className="flex flex-col gap-2 rounded-card border-[1.5px] border-ink bg-surface p-3 shadow-[3px_3px_0_var(--color-ink)]"
    >
      <label htmlFor={id} className="sr-only">
        {t('assistant.composer.label')}
      </label>
      <textarea
        ref={field}
        id={id}
        rows={2}
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={onKeyDown}
        placeholder={t('assistant.composer.placeholder')}
        aria-invalid={tooLong || undefined}
        aria-describedby={`${id}-hint`}
        data-testid="chat-input"
        className="max-h-60 min-h-12 w-full resize-none bg-transparent px-2 py-1.5 text-[0.9375rem] leading-relaxed text-ink outline-none placeholder:text-subtle"
      />
      <div className="flex flex-wrap items-center gap-2">
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">{toolbar}</div>
        <span id={`${id}-hint`} className={cn('text-xs tabular-nums', tooLong ? 'text-danger' : 'sr-only')}>
          {tooLong
            ? t('assistant.composer.tooLong', { max: QUESTION_MAX.toLocaleString(i18n.language) })
            : t('assistant.composer.hint')}
        </span>
        {busy ? (
          <button
            type="button"
            onClick={onStop}
            disabled={!canStop}
            aria-label={t('assistant.composer.stop')}
            title={t('assistant.composer.stop')}
            data-testid="chat-stop"
            className="grid size-11 place-items-center rounded-xl border-[1.5px] border-ink bg-ink text-on-ink disabled:opacity-55"
          >
            <Square className="size-4 fill-current" aria-hidden="true" />
          </button>
        ) : (
          <button
            type="submit"
            disabled={disabled || !trimmed || tooLong}
            aria-label={t('assistant.composer.send')}
            title={t('assistant.composer.send')}
            data-testid="chat-send"
            className="grid size-11 place-items-center rounded-xl border-[1.5px] border-ink bg-accent text-on-accent disabled:cursor-not-allowed disabled:opacity-45"
          >
            <ArrowUp className="size-5" aria-hidden="true" />
          </button>
        )}
      </div>
    </form>
  )
}
