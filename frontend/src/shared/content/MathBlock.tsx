import katex from 'katex'
import { AlertTriangle } from 'lucide-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Một công thức toán (NCL-02-CN-003 TC-01): trong dòng hay đứng riêng. KaTeX dựng cả phần nhìn thấy lẫn MathML cho
 * trình đọc màn hình. Công thức sai cú pháp hiện nguyên văn kèm dấu cảnh báo thay vì làm vỡ trang (TC-04). Lệnh có thể
 * mở đường dẫn hay chèn lớp CSS bị tắt ({@code trust: false}) và độ phức tạp có giới hạn.
 */
export function MathBlock({ tex, display = false }: { tex: string; display?: boolean }) {
  const { t } = useTranslation()
  const rendered = useMemo(() => {
    try {
      return {
        html: katex.renderToString(tex, {
          displayMode: display,
          throwOnError: true,
          trust: false,
          strict: 'ignore',
          maxSize: 20,
          maxExpand: 500,
          output: 'htmlAndMathml',
        }),
      }
    } catch (error) {
      return { error: error instanceof Error ? error.message : String(error) }
    }
  }, [tex, display])

  if ('error' in rendered) {
    const source = display ? `$$${tex}$$` : `$${tex}$`
    const Wrapper = display ? 'div' : 'span'
    return (
      <Wrapper
        className={
          display
            ? 'my-3 flex items-start gap-2 rounded-lg border border-warning/50 bg-warning-soft px-3 py-2'
            : 'inline-flex items-baseline gap-1 rounded-md border border-warning/50 bg-warning-soft px-1.5'
        }
        title={t('content.math.invalid')}
        data-testid="math-error"
      >
        <AlertTriangle className="size-3.5 shrink-0 translate-y-0.5 text-warning" aria-hidden="true" />
        <code className="font-mono text-[0.8125rem] whitespace-pre-wrap [overflow-wrap:anywhere]">{source}</code>
        <span className="sr-only">{t('content.math.invalid')}</span>
      </Wrapper>
    )
  }

  return display ? (
    <div className="my-3 max-w-full overflow-x-auto overflow-y-hidden py-1" data-testid="math-display" dangerouslySetInnerHTML={{ __html: rendered.html }} />
  ) : (
    <span data-testid="math-inline" dangerouslySetInnerHTML={{ __html: rendered.html }} />
  )
}
