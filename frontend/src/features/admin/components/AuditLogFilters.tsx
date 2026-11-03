import { Search, X } from 'lucide-react'
import { useId, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui'
import { AUDIT_ACTIONS, AUDIT_CATEGORIES, AUDIT_OBJECT_TYPES, isAuditCategory } from '../lib/auditCatalog'
import type { AuditLogFilters as Filters } from '../types'

interface AuditLogFiltersProps {
  value: Filters
  onApply: (filters: Filters) => void
}

const fieldClass =
  'h-12 w-full rounded-field border-[1.5px] border-ink bg-surface px-3 text-[0.9375rem] outline-none ' +
  'focus:border-focus focus:ring-4 focus:ring-focus/15 aria-invalid:border-danger'

/**
 * Bộ lọc nhật ký (NCL-03-CN-001): tài khoản (thư điện tử hoặc mã), nhóm và loại thao tác, loại đối tượng, khoảng
 * ngày. Chỉ áp dụng khi bấm Tra cứu, vì mỗi lần tra cứu đều được ghi lại.
 */
export function AuditLogFilters({ value, onApply }: AuditLogFiltersProps) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState<Filters>(value)
  const [rangeError, setRangeError] = useState(false)
  const id = useId()
  const rangeErrorId = `${id}-range`

  const set = (patch: Partial<Filters>) => {
    setDraft((current) => ({ ...current, ...patch }))
    setRangeError(false)
  }
  const actions = draft.category ? AUDIT_ACTIONS[draft.category] : null
  const hasFilters = Object.values(value).some(Boolean)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (draft.from && draft.to && draft.from > draft.to) {
      setRangeError(true)
      return
    }
    onApply({ ...draft, account: draft.account?.trim() || undefined })
  }

  return (
    <form
      onSubmit={submit}
      // Tự kiểm khoảng ngày để báo lỗi bằng lời rõ ràng; min/max trên ô ngày chỉ để bộ chọn ngày làm mờ ngày sai.
      noValidate
      role="search"
      aria-label={t('audit.filters.label')}
      className="grid grid-cols-2 gap-x-3 gap-y-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:gap-x-4 sm:p-5 lg:grid-cols-6"
      data-testid="audit-filters"
    >
      <Field label={t('audit.filters.account')} htmlFor={`${id}-account`} className="col-span-2 lg:col-span-3">
        <input
          id={`${id}-account`}
          type="search"
          value={draft.account ?? ''}
          onChange={(event) => set({ account: event.target.value })}
          placeholder={t('audit.filters.accountHint')}
          autoComplete="off"
          spellCheck={false}
          className={cn(fieldClass, 'placeholder:text-subtle')}
          data-testid="audit-filter-account"
        />
      </Field>
      <Field label={t('audit.filters.category')} htmlFor={`${id}-category`} className="col-span-2 sm:col-span-1 lg:col-span-3">
        <select
          id={`${id}-category`}
          value={draft.category ?? ''}
          onChange={(event) => {
            const category = isAuditCategory(event.target.value) ? event.target.value : undefined
            // Loại thao tác đang chọn không thuộc nhóm mới thì bỏ đi, tránh bộ lọc không bao giờ khớp.
            const keepAction = category && draft.action && AUDIT_ACTIONS[category].includes(draft.action)
            set({ category, action: keepAction ? draft.action : undefined })
          }}
          className={fieldClass}
          data-testid="audit-filter-category"
        >
          <option value="">{t('audit.filters.anyCategory')}</option>
          {AUDIT_CATEGORIES.map((category) => (
            <option key={category} value={category}>
              {t(`audit.categories.${category}`)}
            </option>
          ))}
        </select>
      </Field>
      <Field label={t('audit.filters.action')} htmlFor={`${id}-action`} className="col-span-2 sm:col-span-1 lg:col-span-3">
        <select
          id={`${id}-action`}
          value={draft.action ?? ''}
          onChange={(event) => set({ action: event.target.value || undefined })}
          className={fieldClass}
          data-testid="audit-filter-action"
        >
          <option value="">{t('audit.filters.anyAction')}</option>
          {actions
            ? actions.map((action) => <ActionOption key={action} action={action} />)
            : AUDIT_CATEGORIES.map((category) => (
                <optgroup key={category} label={t(`audit.categories.${category}`)}>
                  {AUDIT_ACTIONS[category].map((action) => (
                    <ActionOption key={action} action={action} />
                  ))}
                </optgroup>
              ))}
        </select>
      </Field>
      <Field label={t('audit.filters.objectType')} htmlFor={`${id}-object`} className="col-span-2 lg:col-span-3">
        <select
          id={`${id}-object`}
          value={draft.objectType ?? ''}
          onChange={(event) => set({ objectType: event.target.value || undefined })}
          className={fieldClass}
          data-testid="audit-filter-object"
        >
          <option value="">{t('audit.filters.anyObject')}</option>
          {AUDIT_OBJECT_TYPES.map((type) => (
            <option key={type} value={type}>
              {t(`audit.objectTypes.${type}`)}
            </option>
          ))}
        </select>
      </Field>
      <Field label={t('audit.filters.from')} htmlFor={`${id}-from`} className="lg:col-span-2">
        <input
          id={`${id}-from`}
          type="date"
          value={draft.from ?? ''}
          max={draft.to}
          onChange={(event) => set({ from: event.target.value || undefined })}
          aria-invalid={rangeError || undefined}
          aria-describedby={rangeError ? rangeErrorId : undefined}
          className={fieldClass}
          data-testid="audit-filter-from"
        />
      </Field>
      <Field label={t('audit.filters.to')} htmlFor={`${id}-to`} className="lg:col-span-2">
        <input
          id={`${id}-to`}
          type="date"
          value={draft.to ?? ''}
          min={draft.from}
          onChange={(event) => set({ to: event.target.value || undefined })}
          aria-invalid={rangeError || undefined}
          aria-describedby={rangeError ? rangeErrorId : undefined}
          className={fieldClass}
          data-testid="audit-filter-to"
        />
      </Field>
      <div className="col-span-2 flex flex-wrap items-end gap-2 lg:justify-end">
        {hasFilters && (
          <Button
            variant="ghost"
            icon={<X className="size-4" aria-hidden="true" />}
            onClick={() => {
              setDraft({})
              setRangeError(false)
              onApply({})
            }}
            data-testid="audit-filter-clear"
          >
            {t('audit.filters.clear')}
          </Button>
        )}
        <Button type="submit" variant="dark" icon={<Search className="size-4" aria-hidden="true" />} data-testid="audit-filter-apply">
          {t('audit.filters.apply')}
        </Button>
      </div>
      {rangeError && (
        <p id={rangeErrorId} role="alert" className="col-span-2 text-sm font-medium text-danger lg:col-span-6">
          {t('audit.filters.rangeInvalid')}
        </p>
      )}
    </form>
  )
}

function ActionOption({ action }: { action: string }) {
  const { t } = useTranslation()
  return <option value={action}>{t(`audit.actions.${action}`, { defaultValue: action })}</option>
}

function Field({
  label,
  htmlFor,
  className,
  children,
}: {
  label: string
  htmlFor: string
  className?: string
  children: ReactNode
}) {
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={htmlFor} className="text-sm font-medium">
        {label}
      </label>
      {children}
    </div>
  )
}
