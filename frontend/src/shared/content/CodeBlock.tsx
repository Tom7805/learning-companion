import { Check, Copy } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from '@/shared/ui'

/**
 * Khối mã nguồn (NCL-02-CN-003 TC-01): tô màu cú pháp (do rehype-highlight dựng sẵn trong {@code children}), tên ngôn
 * ngữ và nút sao chép nguyên văn mã. Dòng dài cuộn ngang trong khung, không đẩy trang rộng ra; khung nhận tiêu điểm để
 * cuộn được bằng bàn phím.
 */
export function CodeBlock({ language, code, children }: { language?: string; code: string; children: ReactNode }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const timer = useRef<number | undefined>(undefined)
  const label = language ? language : t('content.code.plain')

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      window.clearTimeout(timer.current)
      timer.current = window.setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error(t('content.code.copyFailed'))
    }
  }

  return (
    <figure className="my-3 overflow-hidden rounded-field border-[1.5px] border-ink bg-night text-on-night" data-testid="code-block" data-language={language}>
      <figcaption className="flex items-center justify-between gap-2 border-b border-white/15 py-1 pl-4 pr-1.5 text-xs">
        <span className="font-mono text-white/75">{label}</span>
        <button
          type="button"
          onClick={() => void copy()}
          className="inline-flex min-h-9 items-center gap-1.5 rounded-lg px-2.5 font-medium text-white hover:bg-white/10"
          aria-label={copied ? t('content.code.copied') : t('content.code.copyLabel', { language: label })}
          data-testid="code-copy"
        >
          {copied ? <Check className="size-4" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}
          {copied ? t('content.code.copied') : t('content.code.copy')}
        </button>
      </figcaption>
      <pre
        className="rich-code overflow-x-auto px-4 py-3 font-mono text-[0.8125rem] leading-6"
        role="region"
        tabIndex={0}
        aria-label={t('content.code.label', { language: label })}
      >
        {children}
      </pre>
    </figure>
  )
}
