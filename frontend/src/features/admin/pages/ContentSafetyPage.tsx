import { FlaskConical, History, ShieldCheck } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import {
  FlaggedSourcesNote,
  SafetyNotice,
  type ContentSafetyOverview,
  type SafetyLevel,
  type SafetyOutcome,
  type SafetyTrialDone,
} from '@/features/safety'
import { contentSafetyApi } from '@/features/safety/api/contentSafetyApi'
import { useContentSafety, useSafetyLevelsMutation } from '@/features/safety/api/queries'
import { queryKeys } from '@/shared/api/queryKeys'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { formatDateTime } from '@/shared/lib/date'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { Alert, Badge, Button, ConfirmDialog, Skeleton, toast } from '@/shared/ui'
import { AdminField, adminFieldClass } from '../components/AdminDialog'
import { aiErrorMessage } from '../components/aiErrors'

const LEVELS: SafetyLevel[] = ['STRICT', 'STANDARD', 'LENIENT']

const textareaClass =
  'w-full rounded-field border-[1.5px] border-ink bg-surface px-3 py-2.5 text-[0.9375rem] outline-none ' +
  'focus:border-focus focus:ring-4 focus:ring-focus/15'

/**
 * An toàn nội dung (NCL-04-CN-004): ngưỡng lọc của từng nhóm nội dung, chạy thử bộ lọc trên đúng lối của trợ lý hỏi
 * đáp, và các lần bộ lọc can thiệp gần nhất (chỉ loại vi phạm và mã đối tượng). Chỉ quản trị viên mở được; người học
 * gửi yêu cầu đổi ngưỡng bị máy chủ từ chối và ghi nhật ký (TC-05).
 */
export function ContentSafetyPage() {
  const { t } = useTranslation()
  const heading = usePageHeading(t('adminSafety.title'))
  const overview = useContentSafety()

  return (
    <div className="flex flex-col gap-8">
      <header className="flex max-w-3xl flex-col gap-2">
        <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
          {t('adminSafety.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('adminSafety.subtitle')}</p>
      </header>
      {overview.isPending && (
        <div className="grid gap-4 lg:grid-cols-2" data-testid="content-safety-loading">
          <Skeleton className="h-64" />
          <Skeleton className="h-64" />
        </div>
      )}
      {overview.isError && (
        <Alert tone="danger">
          <p>{t('adminSafety.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void overview.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}
      {overview.data && (
        <>
          <div className="grid items-start gap-6 lg:grid-cols-2">
            {/* Khóa theo mốc sửa: máy chủ trả bản mới thì biểu mẫu lấy lại giá trị đã lưu. */}
            <LevelsForm
              key={overview.data.levels.map((level) => `${level.category}:${level.level}`).join()}
              data={overview.data}
            />
            <TrialPanel />
          </div>
          <EventsTable events={overview.data.events} />
        </>
      )}
    </div>
  )
}

function LevelsForm({ data }: { data: ContentSafetyOverview }) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const timeZone = useDisplayTimeZone()
  const [values, setValues] = useState<Record<string, SafetyLevel>>(() =>
    Object.fromEntries(data.levels.map((level) => [level.category, level.level])),
  )
  const [confirming, setConfirming] = useState(false)
  const [touched, setTouched] = useState(false)
  const save = useSafetyLevelsMutation()
  const category = (value: string) => t(`safety.categories.${value}`)
  const levelName = (value: string) => t(`adminSafety.levels.names.${value}`)
  const changed = data.levels.filter((level) => values[level.category] !== level.level)
  const lastChanged = data.levels
    .filter((level) => level.updatedBy && level.updatedAt)
    .sort((left, right) => (right.updatedAt ?? '').localeCompare(left.updatedAt ?? ''))[0]

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (changed.length > 0) setConfirming(true)
  }
  const confirm = () =>
    save.mutate(
      changed.map((level) => ({ category: level.category, level: values[level.category] })),
      {
        onSuccess: () => {
          setConfirming(false)
          toast.success(t('adminSafety.levels.saved'))
        },
        onError: (failure) => {
          setConfirming(false)
          toast.error(t(`errors.${toApiError(failure).code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
        },
      },
    )

  return (
    <section
      aria-labelledby={`${id}-title`}
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
      data-testid="content-safety-levels"
    >
      <div className="flex flex-col gap-1">
        <h2 id={`${id}-title`} className="flex items-center gap-2 text-xl font-semibold">
          <ShieldCheck className="size-5" aria-hidden="true" />
          {t('adminSafety.levels.title')}
        </h2>
        <p className="text-sm text-muted">{t('adminSafety.levels.subtitle')}</p>
      </div>
      <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
        <ul className="flex flex-col divide-y divide-line">
          {data.levels.map((level) => (
            <li
              key={level.category}
              className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between"
              data-category={level.category}
            >
              <div className="flex min-w-0 flex-col gap-0.5">
                <span className="font-medium first-letter:uppercase">{category(level.category)}</span>
                <span className="text-sm text-muted">
                  {t('adminSafety.levels.default', { level: levelName(level.defaultLevel) })}
                </span>
              </div>
              <AdminField
                className="sm:w-56"
                label={t('adminSafety.levels.levelFor', { category: category(level.category) })}
                htmlFor={`${id}-${level.category}`}
                hint={t(`adminSafety.levels.hints.${values[level.category]}`)}
              >
                <select
                  id={`${id}-${level.category}`}
                  className={adminFieldClass}
                  value={values[level.category]}
                  onChange={(event) =>
                    setValues((current) => ({ ...current, [level.category]: event.target.value as SafetyLevel }))
                  }
                >
                  {LEVELS.map((option) => (
                    <option key={option} value={option}>
                      {levelName(option)}
                    </option>
                  ))}
                </select>
              </AdminField>
            </li>
          ))}
          <li className="flex flex-col gap-1 py-3" data-category="SELF_HARM">
            <span className="flex items-center gap-2 font-medium">
              {t('adminSafety.levels.selfHarm')}
              <Badge tone="sky">{t('adminSafety.levels.names.STRICT')}</Badge>
            </span>
            <span className="text-sm text-muted">{t('adminSafety.levels.selfHarmNote')}</span>
          </li>
        </ul>
        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" variant="accent" disabled={changed.length === 0 && !touched}>
            {t('adminSafety.levels.save')}
          </Button>
          {touched && changed.length === 0 && (
            <span className="text-sm text-muted">{t('adminSafety.levels.noChanges')}</span>
          )}
        </div>
      </form>
      {lastChanged?.updatedAt ? (
        <p className="text-sm text-muted" data-testid="content-safety-last-change">
          {t('adminSafety.levels.lastChange', {
            name: lastChanged.updatedBy?.displayName ?? t('adminSafety.levels.unknownActor'),
            time: formatDateTime(lastChanged.updatedAt, i18n.language, timeZone),
          })}
        </p>
      ) : (
        <p className="text-sm text-muted">{t('adminSafety.levels.defaultValues')}</p>
      )}
      <ConfirmDialog
        open={confirming}
        title={t('adminSafety.levels.confirmTitle')}
        description={
          <>
            <span>{t('adminSafety.levels.confirmBody')}</span>
            <ul className="mt-2 flex flex-col gap-1" data-testid="content-safety-changes">
              {changed.map((level) => (
                <li key={level.category}>
                  {t('adminSafety.levels.change', {
                    field: category(level.category),
                    before: levelName(level.level),
                    after: levelName(values[level.category]),
                  })}
                </li>
              ))}
            </ul>
          </>
        }
        confirmLabel={t('adminSafety.levels.apply')}
        cancelLabel={t('adminSafety.levels.cancel')}
        loading={save.isPending}
        onConfirm={confirm}
        onCancel={() => setConfirming(false)}
      />
    </section>
  )
}

function TrialPanel() {
  const { t } = useTranslation()
  const id = useId()
  const client = useQueryClient()
  const [question, setQuestion] = useState('')
  const [source, setSource] = useState('')
  const [output, setOutput] = useState('')
  const [safety, setSafety] = useState<SafetyOutcome | null>(null)
  const [done, setDone] = useState<SafetyTrialDone | null>(null)
  const [errorCode, setErrorCode] = useState<string | null>(null)
  const [running, setRunning] = useState(false)
  const abort = useRef<AbortController | null>(null)

  useEffect(() => () => abort.current?.abort(), [])

  const run = async (event: FormEvent) => {
    event.preventDefault()
    if (!question.trim() || running) return
    abort.current?.abort()
    abort.current = new AbortController()
    setOutput('')
    setSafety(null)
    setDone(null)
    setErrorCode(null)
    setRunning(true)
    try {
      await contentSafetyApi.trial(
        { question: question.trim(), ...(source.trim() ? { source: source.trim() } : {}) },
        {
          onChunk: (text) => setOutput((current) => current + text),
          onSafety: (outcome) => {
            setOutput('')
            setSafety(outcome)
          },
          onDone: setDone,
          onError: setErrorCode,
        },
        abort.current.signal,
      )
    } catch (failure) {
      if (!(failure instanceof DOMException && failure.name === 'AbortError')) setErrorCode('NETWORK_ERROR')
    } finally {
      setRunning(false)
      void client.invalidateQueries({ queryKey: queryKeys.contentSafety })
    }
  }

  return (
    <section
      aria-labelledby={`${id}-title`}
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
      data-testid="content-safety-trial"
    >
      <div className="flex flex-col gap-1">
        <h2 id={`${id}-title`} className="flex items-center gap-2 text-xl font-semibold">
          <FlaskConical className="size-5" aria-hidden="true" />
          {t('adminSafety.trial.title')}
        </h2>
        <p className="text-sm text-muted">{t('adminSafety.trial.subtitle')}</p>
      </div>
      <form onSubmit={run} className="flex flex-col gap-3">
        <AdminField label={t('adminSafety.trial.question')} htmlFor={`${id}-question`}>
          <textarea
            id={`${id}-question`}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            maxLength={2000}
            rows={2}
            className={textareaClass}
          />
        </AdminField>
        <AdminField
          label={t('adminSafety.trial.source')}
          htmlFor={`${id}-source`}
          hint={t('adminSafety.trial.sourceHint')}
        >
          <textarea
            id={`${id}-source`}
            value={source}
            onChange={(event) => setSource(event.target.value)}
            maxLength={4000}
            rows={3}
            className={textareaClass}
          />
        </AdminField>
        <div>
          <Button
            type="submit"
            variant="dark"
            loading={running}
            disabled={!question.trim()}
            icon={<FlaskConical className="size-4" aria-hidden="true" />}
          >
            {t('adminSafety.trial.run')}
          </Button>
        </div>
      </form>
      {safety ? (
        <SafetyNotice outcome={safety} />
      ) : (
        <div
          aria-live="polite"
          aria-busy={running || undefined}
          className="min-h-16 whitespace-pre-wrap rounded-field border border-line bg-paper px-4 py-3 text-[0.9375rem] leading-relaxed"
          data-testid="content-safety-trial-output"
        >
          {output || (
            <span className="text-muted">{t(running ? 'adminSafety.trial.waiting' : 'adminSafety.trial.empty')}</span>
          )}
        </div>
      )}
      {done && <FlaggedSourcesNote sources={done.flaggedSources ?? []} />}
      {done?.status === 'ANSWERED' && done.providerCode && (
        <p className="text-sm text-ink-soft" data-testid="content-safety-trial-done">
          {t('adminSafety.trial.answered', { provider: done.providerCode })}
        </p>
      )}
      {errorCode && (
        <Alert tone="danger" data-testid="content-safety-trial-error">
          <p>{aiErrorMessage(t, { code: errorCode, message: '', status: 0, fieldErrors: [] })}</p>
        </Alert>
      )}
    </section>
  )
}

function EventsTable({ events }: { events: ContentSafetyOverview['events'] }) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const timeZone = useDisplayTimeZone()
  const tone = (kind: string): 'sky' | 'lilac' | 'sun' =>
    kind === 'SELF_HARM_SUPPORT' ? 'sky' : kind === 'INJECTION_FLAGGED' ? 'lilac' : 'sun'

  return (
    <section
      aria-labelledby={`${id}-title`}
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
      data-testid="content-safety-events"
    >
      <div className="flex flex-col gap-1">
        <h2 id={`${id}-title`} className="flex items-center gap-2 text-xl font-semibold">
          <History className="size-5" aria-hidden="true" />
          {t('adminSafety.events.title')}
        </h2>
        <p className="text-sm text-muted">{t('adminSafety.events.subtitle')}</p>
      </div>
      {events.length === 0 ? (
        <p className="text-sm text-muted">{t('adminSafety.events.empty')}</p>
      ) : (
        <div className="overflow-x-auto" tabIndex={0} aria-labelledby={`${id}-title`}>
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="text-ink-soft">
              <tr className="border-b border-line">
                <th scope="col" className="py-2 pr-3 font-medium">{t('adminSafety.events.time')}</th>
                <th scope="col" className="py-2 pr-3 font-medium">{t('adminSafety.events.kind')}</th>
                <th scope="col" className="py-2 pr-3 font-medium">{t('adminSafety.events.category')}</th>
                <th scope="col" className="py-2 pr-3 font-medium">{t('adminSafety.events.feature')}</th>
                <th scope="col" className="py-2 font-medium">{t('adminSafety.events.object')}</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id} className="border-b border-line last:border-0" data-kind={event.kind}>
                  <td className="py-2 pr-3 whitespace-nowrap">
                    {formatDateTime(event.occurredAt, i18n.language, timeZone)}
                  </td>
                  <td className="py-2 pr-3">
                    <Badge tone={tone(event.kind)}>{t(`adminSafety.events.kinds.${event.kind}`)}</Badge>
                  </td>
                  <td className="py-2 pr-3 first-letter:uppercase">
                    {event.signal
                      ? t(`adminSafety.events.signals.${event.signal}`)
                      : event.category
                        ? t(`safety.categories.${event.category}`)
                        : '—'}
                  </td>
                  <td className="py-2 pr-3 font-mono text-xs">{event.feature}</td>
                  <td className="py-2 font-mono text-xs break-all">
                    {event.objectType
                      ? `${t(`audit.objectTypes.${event.objectType}`, { defaultValue: event.objectType })} · ${event.objectId ?? ''}`
                      : event.id}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
