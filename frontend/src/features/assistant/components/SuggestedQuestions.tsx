import { CornerDownRight } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'

/** Ba câu hỏi gợi ý ở cuối câu trả lời: bấm là hỏi tiếp ngay. */
export function SuggestedQuestions({
  questions,
  disabled,
  onPick,
}: {
  questions: string[]
  disabled?: boolean
  onPick: (question: string) => void
}) {
  const { t } = useTranslation()
  const id = useId()
  return (
    <section aria-labelledby={id} className="flex flex-col gap-1.5" data-testid="suggested-questions">
      <h3 id={id} className="text-xs font-semibold uppercase tracking-wide text-muted">
        {t('assistant.answer.suggestions')}
      </h3>
      <ul className="flex flex-col gap-1.5">
        {questions.map((question) => (
          <li key={question}>
            <button
              type="button"
              disabled={disabled}
              onClick={() => onPick(question)}
              data-testid="suggested-question"
              className="flex min-h-11 w-full items-center gap-2 rounded-xl border-[1.5px] border-line bg-surface px-3 py-2 text-left text-sm hover:border-ink hover:bg-paper disabled:opacity-55"
            >
              <CornerDownRight className="size-4 shrink-0 text-muted" aria-hidden="true" />
              <span className="[overflow-wrap:anywhere]">{question}</span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
