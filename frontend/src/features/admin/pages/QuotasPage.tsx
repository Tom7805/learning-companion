import { Gauge, ListOrdered } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { quotaApi } from '@/features/quota/api/quotaApi'
import { useAdminQuota, useAdminQuotaMutation } from '@/features/quota/api/queries'
import type { AdminQuota } from '@/features/quota/types'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { formatDateTime } from '@/shared/lib/date'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { Alert, Badge, Button, ConfirmDialog, Skeleton, TextField, toast } from '@/shared/ui'

const MEBIBYTE = 1024 * 1024

type Change = { label: string; before: number | string; after: number | string }

/**
 * Hạn mức công bằng (NCL-04-CN-003): số lượt trí tuệ nhân tạo mỗi ngày và dung lượng lưu trữ chung cho mọi người
 * học, cùng bảng quy đổi lượt của từng loại tác vụ. Mỗi lần lưu phải xác nhận lại các thay đổi và được ghi nhật ký
 * với người thực hiện, giá trị trước sau và thời điểm (TC-06).
 */
export function QuotasPage() {
  const { t } = useTranslation()
  const heading = usePageHeading(t('adminQuota.title'))
  const quota = useAdminQuota()

  return (
    <div className="flex flex-col gap-8">
      <header className="flex max-w-3xl flex-col gap-2">
        <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
          {t('adminQuota.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('adminQuota.subtitle')}</p>
      </header>
      {quota.isPending && (
        <div className="grid gap-4 lg:grid-cols-2" data-testid="admin-quota-loading">
          <Skeleton className="h-64" />
          <Skeleton className="h-64" />
        </div>
      )}
      {quota.isError && (
        <Alert tone="danger">
          <p>{t('adminQuota.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void quota.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}
      {quota.data && (
        <div className="grid items-start gap-6 lg:grid-cols-[2fr_3fr]">
          {/* Khóa theo mốc sửa: máy chủ trả bản mới thì biểu mẫu lấy lại giá trị đã lưu. */}
          <PolicyForm key={`policy-${quota.data.policy.updatedAt}`} data={quota.data} />
          <CostsForm key={`costs-${quota.data.costs.map((cost) => cost.updatedAt).join()}`} data={quota.data} />
        </div>
      )}
    </div>
  )
}

function LastChange({ at, by }: { at: string; by?: AdminQuota['policy']['updatedBy'] }) {
  const { t, i18n } = useTranslation()
  const timeZone = useDisplayTimeZone()
  if (!by) return <p className="text-sm text-muted">{t('adminQuota.defaultValues')}</p>
  return (
    <p className="text-sm text-muted" data-testid="admin-quota-last-change">
      {t('adminQuota.lastChange', {
        name: by.displayName ?? t('adminQuota.unknownActor'),
        time: formatDateTime(at, i18n.language, timeZone),
      })}
    </p>
  )
}

function ChangeList({ changes }: { changes: Change[] }) {
  const { t } = useTranslation()
  return (
    <ul className="mt-2 flex flex-col gap-1" data-testid="admin-quota-changes">
      {changes.map((change) => (
        <li key={change.label}>
          {t('adminQuota.confirm.change', { field: change.label, before: change.before, after: change.after })}
        </li>
      ))}
    </ul>
  )
}

function integer(value: string) {
  return /^\d+$/.test(value.trim()) ? Number(value.trim()) : Number.NaN
}

function PolicyForm({ data }: { data: AdminQuota }) {
  const { t } = useTranslation()
  const currentMb = Math.round(data.policy.storageLimitBytes / MEBIBYTE)
  const [daily, setDaily] = useState(String(data.policy.dailyAiUnits))
  const [storage, setStorage] = useState(String(currentMb))
  const [confirming, setConfirming] = useState(false)
  const save = useAdminQuotaMutation(quotaApi.updatePolicy)
  const dailyValue = integer(daily)
  const storageValue = integer(storage)
  const dailyError = Number.isInteger(dailyValue) && dailyValue >= 1 && dailyValue <= 10000
    ? undefined : t('adminQuota.policy.dailyInvalid')
  const storageError = Number.isInteger(storageValue) && storageValue >= 10 && storageValue <= 1048576
    ? undefined : t('adminQuota.policy.storageInvalid')
  const changes: Change[] = [
    ...(dailyValue !== data.policy.dailyAiUnits
      ? [{ label: t('adminQuota.policy.daily'), before: data.policy.dailyAiUnits, after: dailyValue }] : []),
    ...(storageValue !== currentMb
      ? [{ label: t('adminQuota.policy.storage'), before: currentMb, after: storageValue }] : []),
  ]
  const [touched, setTouched] = useState(false)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (dailyError || storageError || changes.length === 0) return
    setConfirming(true)
  }
  const confirm = () =>
    save.mutate(
      { dailyAiUnits: dailyValue, storageLimitMb: storageValue },
      {
        onSuccess: () => {
          setConfirming(false)
          toast.success(t('adminQuota.policy.saved'))
        },
        onError: (failure) => {
          setConfirming(false)
          toast.error(t(`errors.${toApiError(failure).code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
        },
      },
    )

  return (
    <section
      aria-labelledby="admin-quota-policy-title"
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-5"
      data-testid="admin-quota-policy"
    >
      <div className="flex flex-col gap-1">
        <h2 id="admin-quota-policy-title" className="flex items-center gap-2 text-xl font-semibold">
          <Gauge className="size-5" aria-hidden="true" />
          {t('adminQuota.policy.title')}
        </h2>
        <p className="text-sm text-muted">{t('adminQuota.policy.subtitle')}</p>
      </div>
      <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
        <TextField
          label={t('adminQuota.policy.daily')}
          inputMode="numeric"
          value={daily}
          onChange={(event) => setDaily(event.target.value)}
          error={touched ? dailyError : undefined}
          hint={t('adminQuota.policy.dailyHint')}
        />
        <TextField
          label={t('adminQuota.policy.storage')}
          inputMode="numeric"
          value={storage}
          onChange={(event) => setStorage(event.target.value)}
          error={touched ? storageError : undefined}
          hint={t('adminQuota.policy.storageHint')}
        />
        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" variant="accent" disabled={changes.length === 0 && !touched}>
            {t('adminQuota.policy.save')}
          </Button>
          {touched && changes.length === 0 && !dailyError && !storageError && (
            <span className="text-sm text-muted">{t('adminQuota.noChanges')}</span>
          )}
        </div>
      </form>
      <LastChange at={data.policy.updatedAt} by={data.policy.updatedBy} />
      <ConfirmDialog
        open={confirming}
        title={t('adminQuota.confirm.policyTitle')}
        description={
          <>
            <span>{t('adminQuota.confirm.policyBody')}</span>
            <ChangeList changes={changes} />
          </>
        }
        confirmLabel={t('adminQuota.confirm.apply')}
        cancelLabel={t('adminQuota.cancel')}
        loading={save.isPending}
        onConfirm={confirm}
        onCancel={() => setConfirming(false)}
      />
    </section>
  )
}

function CostsForm({ data }: { data: AdminQuota }) {
  const { t, i18n } = useTranslation()
  const timeZone = useDisplayTimeZone()
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(data.costs.map((cost) => [cost.task, String(cost.units)])),
  )
  const [touched, setTouched] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const save = useAdminQuotaMutation(quotaApi.updateCosts)
  const name = (task: string) => t(`quota.tasks.${task}`, { defaultValue: task })
  const errors = Object.fromEntries(
    data.costs.map((cost) => {
      const value = integer(values[cost.task] ?? '')
      return [cost.task, Number.isInteger(value) && value >= 1 && value <= 100 ? undefined
        : t('adminQuota.costs.invalid')]
    }),
  )
  const changed = data.costs
    .map((cost) => ({ cost, value: integer(values[cost.task] ?? '') }))
    .filter(({ cost, value }) => value !== cost.units)
  const lastChanged = data.costs
    .filter((cost) => cost.updatedBy)
    .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))[0]

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (Object.values(errors).some(Boolean) || changed.length === 0) return
    setConfirming(true)
  }
  const confirm = () =>
    save.mutate(
      changed.map(({ cost, value }) => ({ task: cost.task, units: value })),
      {
        onSuccess: () => {
          setConfirming(false)
          toast.success(t('adminQuota.costs.saved'))
        },
        onError: (failure) => {
          setConfirming(false)
          toast.error(t(`errors.${toApiError(failure).code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
        },
      },
    )

  return (
    <section
      aria-labelledby="admin-quota-costs-title"
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-5"
      data-testid="admin-quota-costs"
    >
      <div className="flex flex-col gap-1">
        <h2 id="admin-quota-costs-title" className="flex items-center gap-2 text-xl font-semibold">
          <ListOrdered className="size-5" aria-hidden="true" />
          {t('adminQuota.costs.title')}
        </h2>
        <p className="text-sm text-muted">{t('adminQuota.costs.subtitle')}</p>
      </div>
      <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
        <ul className="flex flex-col divide-y divide-line">
          {data.costs.map((cost) => (
            <li
              key={cost.task}
              className="flex flex-col gap-3 py-3 sm:flex-row sm:items-start sm:justify-between"
              data-task={cost.task}
            >
              <div className="flex min-w-0 flex-col gap-1">
                <span className="font-medium">{name(cost.task)}</span>
                <span className="flex flex-wrap items-center gap-2 text-sm text-muted">
                  <Badge tone={cost.weight === 'LONG' ? 'lilac' : 'sky'}>{t(`quota.weights.${cost.weight}`)}</Badge>
                  {t('adminQuota.costs.default', { count: cost.defaultUnits })}
                </span>
              </div>
              <TextField
                className="sm:w-40"
                label={t('adminQuota.costs.unitsFor', { task: name(cost.task) })}
                inputMode="numeric"
                value={values[cost.task] ?? ''}
                onChange={(event) => setValues((current) => ({ ...current, [cost.task]: event.target.value }))}
                error={touched ? errors[cost.task] : undefined}
              />
            </li>
          ))}
        </ul>
        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" variant="accent" disabled={changed.length === 0 && !touched}>
            {t('adminQuota.costs.save')}
          </Button>
          {touched && changed.length === 0 && (
            <span className="text-sm text-muted">{t('adminQuota.noChanges')}</span>
          )}
        </div>
      </form>
      {lastChanged ? (
        <p className="text-sm text-muted" data-testid="admin-quota-costs-last-change">
          {t('adminQuota.lastChange', {
            name: lastChanged.updatedBy?.displayName ?? t('adminQuota.unknownActor'),
            time: formatDateTime(lastChanged.updatedAt, i18n.language, timeZone),
          })}
        </p>
      ) : (
        <p className="text-sm text-muted">{t('adminQuota.defaultValues')}</p>
      )}
      <ConfirmDialog
        open={confirming}
        title={t('adminQuota.confirm.costsTitle')}
        description={
          <>
            <span>{t('adminQuota.confirm.costsBody')}</span>
            <ChangeList
              changes={changed.map(({ cost, value }) => ({ label: name(cost.task), before: cost.units, after: value }))}
            />
          </>
        }
        confirmLabel={t('adminQuota.confirm.apply')}
        cancelLabel={t('adminQuota.cancel')}
        loading={save.isPending}
        onConfirm={confirm}
        onCancel={() => setConfirming(false)}
      />
    </section>
  )
}
