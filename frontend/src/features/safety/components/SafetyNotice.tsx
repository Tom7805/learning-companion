import { HeartHandshake, Phone, ShieldAlert, ShieldQuestion } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import type { SafetyOutcome } from '../types'

/**
 * Thông báo khi bộ lọc an toàn can thiệp (NCL-04-CN-004), dùng chung cho mọi chỗ hiện nội dung do AI sinh ra. Câu hỏi
 * bị chặn: nêu lý do và gợi ý cách hỏi khác (TC-02). Nội dung sinh ra vi phạm: thay hẳn câu trả lời (TC-04). Dấu hiệu
 * tự gây hại: lời ân cần cùng nguồn trợ giúp gọi được ngay, không phải lời từ chối (TC-03).
 */
export function SafetyNotice({ outcome }: { outcome: SafetyOutcome }) {
  const { t } = useTranslation()
  const id = useId()
  if (outcome.status === 'SUPPORT' && outcome.support) {
    const support = outcome.support
    return (
      <section
        aria-labelledby={`${id}-title`}
        className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-sky-soft p-4 sm:p-5"
        data-testid="safety-support"
      >
        <h3 id={`${id}-title`} className="flex items-center gap-2 text-lg font-semibold">
          <HeartHandshake className="size-5" aria-hidden="true" />
          {t('safety.support.title')}
        </h3>
        <p className="leading-relaxed" lang={support.language}>
          {support.message}
        </p>
        <div className="flex flex-col gap-2">
          <h4 className="text-sm font-semibold text-ink-soft">{t('safety.support.resources')}</h4>
          <ul className="grid gap-2 sm:grid-cols-3" lang={support.language}>
            {support.resources.map((resource) => (
              <li
                key={resource.name}
                className="flex flex-col gap-1 rounded-field border border-line bg-surface px-3 py-2.5"
              >
                <span className="font-medium">{resource.name}</span>
                {resource.contact && (
                  <a
                    href={`tel:${resource.contact}`}
                    className="inline-flex w-fit items-center gap-1.5 text-lg font-semibold underline underline-offset-2"
                    aria-label={t('safety.support.call', { contact: resource.contact })}
                  >
                    <Phone className="size-4" aria-hidden="true" />
                    {resource.contact}
                  </a>
                )}
                <span className="text-sm text-ink-soft">{resource.note}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>
    )
  }

  const category = outcome.category ?? 'HARMFUL_INSTRUCTIONS'
  const output = outcome.stage === 'OUTPUT'
  const suggestions = t(`safety.suggestions.${category}`, { returnObjects: true, defaultValue: [] }) as string[]
  const Icon = output ? ShieldQuestion : ShieldAlert
  return (
    <section
      role="alert"
      aria-labelledby={`${id}-title`}
      className="flex flex-col gap-3 rounded-card border-[1.5px] border-ink bg-warning-soft p-4 sm:p-5"
      data-testid="safety-blocked"
      data-stage={output ? 'OUTPUT' : 'INPUT'}
      data-category={category}
    >
      <h3 id={`${id}-title`} className="flex items-center gap-2 text-lg font-semibold">
        <Icon className="size-5" aria-hidden="true" />
        {t(output ? 'safety.blocked.outputTitle' : 'safety.blocked.inputTitle')}
      </h3>
      <p className="leading-relaxed">
        {t(output ? 'safety.blocked.outputReason' : 'safety.blocked.inputReason', {
          category: t(`safety.categories.${category}`),
        })}
      </p>
      {Array.isArray(suggestions) && suggestions.length > 0 && (
        <div className="flex flex-col gap-1">
          <p className="text-sm font-semibold text-ink-soft">{t('safety.blocked.suggestionsTitle')}</p>
          <ul className="list-disc pl-5 text-sm leading-relaxed">
            {suggestions.map((suggestion) => (
              <li key={suggestion}>{suggestion}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

/** Ghi chú khi có đoạn nguồn bị gắn cờ nghi chèn chỉ dẫn (TC-01). */
export function FlaggedSourcesNote({ sources }: { sources: number[] }) {
  const { t } = useTranslation()
  if (sources.length === 0) return null
  return (
    <p
      className="rounded-field border border-line bg-sun-soft px-3 py-2 text-sm text-ink-soft"
      data-testid="safety-flagged-sources"
    >
      {t('safety.flagged', { list: sources.map((source) => `[${source}]`).join(', ') })}
    </p>
  )
}
