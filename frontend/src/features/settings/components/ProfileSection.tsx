import { Camera, CircleAlert, CircleCheck, Globe2, Loader2, Trash2 } from 'lucide-react'
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { useAutosave, type AutosaveStatus } from '@/shared/hooks/useAutosave'
import { cn } from '@/shared/lib/cn'
import { deviceTimeZone, normalizeTimeZone, timeZoneLabel, timeZoneOptions } from '@/shared/lib/timezone'
import { Alert, Avatar, Button, TextField } from '@/shared/ui'
import { useRemoveAvatar, useUpdateProfile, useUploadAvatar } from '../api/queries'
import { AVATAR_ACCEPT, ImageFileError, loadImageFile } from '../lib/image'
import type { AssistantLanguage, Profile, ProfileChanges } from '../types'
import { AvatarCropper } from './AvatarCropper'

const DISPLAY_NAME_MAX = 50
const NAME_SAVE_DELAY = 700
const GOAL_PRESETS = [15, 30, 45, 60, 90] as const
const GOAL_MIN = 5
const GOAL_MAX = 600

type Field = keyof ProfileChanges | 'avatar'
type FieldErrors = Partial<Record<Field, string>>

const length = (value: string) => [...value.trim().replace(/\s+/g, ' ')].length

/** Hồ sơ và tùy chọn học tập (NCL-01-CN-005): mọi thay đổi lưu ngay, không có nút "Lưu". */
export function ProfileSection({ profile }: { profile: Profile }) {
  const { t } = useTranslation()
  const update = useUpdateProfile()
  const { status, track } = useAutosave()
  const [errors, setErrors] = useState<FieldErrors>({})
  const [timeZoneNotice, setTimeZoneNotice] = useState<{ from?: string | null; to: string } | null>(null)

  const save = async (field: Field, changes: ProfileChanges) => {
    try {
      const saved = await track(() => update.mutateAsync({ id: profile.id, changes }))
      setErrors((current) => ({ ...current, [field]: undefined }))
      return saved
    } catch (error) {
      setErrors((current) => ({ ...current, [field]: messageFor(toApiError(error)) }))
      return null
    }
  }

  const messageFor = (error: ApiError) => t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })

  const changeTimeZone = async (zone: string) => {
    const previous = profile.timeZone
    const saved = await save('timeZone', { timeZone: zone })
    if (saved) setTimeZoneNotice({ from: previous, to: zone })
  }

  return (
    <div className="flex flex-col gap-7">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="text-2xl tracking-tight">{t('profile.title')}</h2>
          <p className="text-sm text-muted">{t('profile.subtitle')}</p>
        </div>
        <SaveStatus status={status} />
      </header>

      <AvatarField profile={profile} track={track} />

      <DisplayNameField
        key={profile.id}
        initial={profile.displayName}
        error={errors.displayName}
        onSave={(displayName) => save('displayName', { displayName })}
        onInvalid={(message) => setErrors((current) => ({ ...current, displayName: message }))}
      />

      <TimeZoneField
        value={profile.timeZone}
        error={errors.timeZone}
        notice={timeZoneNotice}
        onChange={(zone) => void changeTimeZone(zone)}
      />

      <AssistantLanguageField
        value={profile.assistantLanguage}
        error={errors.assistantLanguage}
        onChange={(assistantLanguage) => void save('assistantLanguage', { assistantLanguage })}
      />

      <DailyGoalField
        value={profile.dailyGoalMinutes}
        error={errors.dailyGoalMinutes}
        onChange={(dailyGoalMinutes) => void save('dailyGoalMinutes', { dailyGoalMinutes })}
        onInvalid={(message) => setErrors((current) => ({ ...current, dailyGoalMinutes: message }))}
      />

      <RolloverField
        value={profile.dayRolloverHour}
        error={errors.dayRolloverHour}
        onChange={(dayRolloverHour) => void save('dayRolloverHour', { dayRolloverHour })}
      />
    </div>
  )
}

function SaveStatus({ status }: { status: AutosaveStatus }) {
  const { t } = useTranslation()
  if (status === 'idle') return <p className="text-sm text-muted">{t('profile.autosave')}</p>
  return (
    <p
      role="status"
      data-testid="save-status"
      data-status={status}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border-[1.5px] px-3 py-1 text-sm font-medium',
        status === 'error' ? 'border-danger/40 bg-danger-soft text-danger' : 'border-ink/15 bg-success-soft text-success',
        status === 'saving' && 'bg-paper text-ink-soft',
      )}
    >
      {status === 'saving' && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
      {status === 'saved' && <CircleCheck className="size-4" aria-hidden="true" />}
      {status === 'error' && <CircleAlert className="size-4" aria-hidden="true" />}
      {t(`profile.status.${status}`)}
    </p>
  )
}

/** Một khối tùy chọn có nhãn, mô tả và lỗi; giữ bố cục giống nhau cho mọi trường. */
function Setting({
  label,
  description,
  error,
  htmlFor,
  children,
}: {
  label: string
  description?: ReactNode
  error?: string
  htmlFor?: string
  children: ReactNode
}) {
  const labelId = useId()
  return (
    // Nhãn đã gắn với ô nhập thì vùng bao không dùng lại, để trình đọc màn hình không đọc hai lần.
    <section aria-labelledby={htmlFor ? undefined : labelId} className="flex flex-col gap-2.5 border-t border-line pt-6">
      <div className="flex flex-col gap-0.5">
        {htmlFor ? (
          <label id={labelId} htmlFor={htmlFor} className="font-medium">
            {label}
          </label>
        ) : (
          <h3 id={labelId} className="font-medium">
            {label}
          </h3>
        )}
        {description && <div className="text-sm leading-relaxed text-muted">{description}</div>}
      </div>
      {children}
      {error && (
        <p role="alert" className="flex items-center gap-1.5 text-sm text-danger">
          <CircleAlert className="size-4 shrink-0" aria-hidden="true" />
          {error}
        </p>
      )}
    </section>
  )
}

function AvatarField({ profile, track }: { profile: Profile; track: ReturnType<typeof useAutosave>['track'] }) {
  const { t } = useTranslation()
  const upload = useUploadAvatar()
  const remove = useRemoveAvatar()
  const input = useRef<HTMLInputElement>(null)
  const [image, setImage] = useState<HTMLImageElement | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!image) return undefined
    return () => URL.revokeObjectURL(image.src)
  }, [image])

  const pick = async (file: File | undefined) => {
    if (!file) return
    setError(null)
    try {
      setImage(await loadImageFile(file))
    } catch (caught) {
      setError(
        caught instanceof ImageFileError && caught.reason === 'size'
          ? t('profile.avatar.tooLarge')
          : t('errors.AVATAR_INVALID_FORMAT'),
      )
    }
  }

  const confirm = async (blob: Blob) => {
    try {
      await track(() => upload.mutateAsync({ id: profile.id, image: blob }))
      setImage(null)
    } catch (caught) {
      setImage(null)
      setError(t(`errors.${toApiError(caught).code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
    }
  }

  const clear = async () => {
    setError(null)
    try {
      await track(() => remove.mutateAsync(profile.id))
    } catch (caught) {
      setError(t(`errors.${toApiError(caught).code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
    }
  }

  return (
    <section aria-labelledby="avatar-heading" className="flex flex-wrap items-center gap-5">
      <Avatar name={profile.displayName} src={profile.avatarUrl} size="xl" />
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <h3 id="avatar-heading" className="font-medium">
          {t('profile.avatar.label')}
        </h3>
        <p className="text-sm text-muted" id="avatar-hint">
          {t('profile.avatar.hint')}
        </p>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="dark"
            onClick={() => input.current?.click()}
            icon={<Camera className="size-4" aria-hidden="true" />}
            aria-describedby="avatar-hint"
          >
            {profile.avatarUrl ? t('profile.avatar.change') : t('profile.avatar.upload')}
          </Button>
          {profile.avatarUrl && (
            <Button
              variant="outline"
              onClick={() => void clear()}
              loading={remove.isPending}
              icon={<Trash2 className="size-4" aria-hidden="true" />}
            >
              {t('profile.avatar.remove')}
            </Button>
          )}
        </div>
        <input
          ref={input}
          type="file"
          accept={AVATAR_ACCEPT}
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
          data-testid="avatar-input"
          onChange={(event) => {
            void pick(event.target.files?.[0])
            event.target.value = ''
          }}
        />
        {error && (
          <p role="alert" className="flex items-center gap-1.5 text-sm text-danger" data-testid="avatar-error">
            <CircleAlert className="size-4 shrink-0" aria-hidden="true" />
            {error}
          </p>
        )}
      </div>
      <AvatarCropper
        key={image?.src ?? 'none'}
        image={image}
        saving={upload.isPending}
        onCancel={() => setImage(null)}
        onConfirm={(blob) => void confirm(blob)}
      />
    </section>
  )
}

function DisplayNameField({
  initial,
  error,
  onSave,
  onInvalid,
}: {
  initial: string
  error?: string
  onSave: (name: string) => Promise<unknown>
  onInvalid: (message: string | undefined) => void
}) {
  const { t } = useTranslation()
  const [value, setValue] = useState(initial)
  const lastSaved = useRef(initial)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const count = length(value)

  useEffect(() => () => clearTimeout(timer.current), [])

  const validate = (name: string) => {
    if (length(name) === 0) return t('validation.displayNameRequired')
    if (length(name) > DISPLAY_NAME_MAX) return t('validation.displayNameTooLong')
    return undefined
  }

  const commit = (name: string) => {
    clearTimeout(timer.current)
    const problem = validate(name)
    onInvalid(problem)
    const normalized = name.trim().replace(/\s+/g, ' ')
    if (problem || normalized === lastSaved.current) return
    lastSaved.current = normalized
    void onSave(normalized)
  }

  return (
    <TextField
      label={t('profile.displayName.label')}
      value={value}
      autoComplete="nickname"
      error={error}
      hint={
        <span className={cn('tabular-nums', count > DISPLAY_NAME_MAX && 'font-medium text-danger')} data-testid="name-count">
          {t('profile.displayName.count', { count, max: DISPLAY_NAME_MAX })}
        </span>
      }
      onChange={(event) => {
        const next = event.target.value
        setValue(next)
        clearTimeout(timer.current)
        // Báo lỗi độ dài ngay khi gõ, còn lưu thì đợi người học ngừng gõ một chút.
        if (length(next) > DISPLAY_NAME_MAX) {
          onInvalid(t('validation.displayNameTooLong'))
          return
        }
        timer.current = setTimeout(() => commit(next), NAME_SAVE_DELAY)
      }}
      onBlur={() => commit(value)}
      onKeyDown={(event) => {
        if (event.key === 'Enter') commit(value)
      }}
    />
  )
}

function TimeZoneField({
  value,
  error,
  notice,
  onChange,
}: {
  value?: string | null
  error?: string
  notice: { from?: string | null; to: string } | null
  onChange: (zone: string) => void
}) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const device = deviceTimeZone()
  const current = value ? normalizeTimeZone(value) : null
  const [options] = useState(() => timeZoneOptions([device, current]))
  const suggest = current !== device

  return (
    <Setting label={t('profile.timeZone.label')} description={t('profile.timeZone.description')} error={error} htmlFor={id}>
      <select
        id={id}
        value={current ?? ''}
        onChange={(event) => onChange(event.target.value)}
        data-testid="time-zone"
        className="h-12 w-full rounded-field border-[1.5px] border-ink bg-surface px-3 text-[0.9375rem] outline-none focus:border-focus focus:ring-4 focus:ring-focus/15"
      >
        {!current && <option value="">{t('profile.timeZone.notSet')}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {suggest && (
        <div
          className="flex flex-wrap items-center justify-between gap-3 rounded-field bg-sky-soft px-4 py-3 text-sm"
          data-testid="time-zone-suggestion"
        >
          <span className="flex items-center gap-2">
            <Globe2 className="size-4 shrink-0" aria-hidden="true" />
            {t('profile.timeZone.device', { zone: timeZoneLabel(device) })}
          </span>
          <Button variant="outline" className="h-9 px-3 text-sm" onClick={() => onChange(device)}>
            {t('profile.timeZone.useDevice')}
          </Button>
        </div>
      )}
      {notice && (
        <Alert tone="info" key={notice.to} data-testid="time-zone-notice">
          <p className="font-medium">{t('profile.timeZone.changed', { zone: timeZoneLabel(notice.to) })}</p>
          {notice.from && notice.from !== notice.to && (
            <p className="mt-1">
              {t('profile.timeZone.example', {
                before: clock(i18n.language, notice.from),
                after: clock(i18n.language, notice.to),
              })}
            </p>
          )}
        </Alert>
      )}
    </Setting>
  )
}

function clock(language: string, timeZone: string) {
  return new Intl.DateTimeFormat(language, { hour: '2-digit', minute: '2-digit', timeZone }).format(new Date())
}

function AssistantLanguageField({
  value,
  error,
  onChange,
}: {
  value: AssistantLanguage
  error?: string
  onChange: (language: AssistantLanguage) => void
}) {
  const { t } = useTranslation()
  const name = useId()
  return (
    <Setting label={t('profile.language.label')} description={t('profile.language.description')} error={error}>
      <div role="radiogroup" aria-label={t('profile.language.label')} className="grid gap-2 sm:grid-cols-2">
        {(['vi', 'en'] as const).map((language) => (
          <label
            key={language}
            className={cn(
              'flex cursor-pointer items-center gap-3 rounded-field border-[1.5px] px-4 py-3 transition-colors',
              value === language ? 'border-ink bg-lilac-soft' : 'border-line hover:border-ink',
            )}
          >
            <input
              type="radio"
              name={name}
              value={language}
              checked={value === language}
              onChange={() => onChange(language)}
              className="size-4 accent-ink"
            />
            <span className="font-medium">{t(`profile.language.${language}`)}</span>
          </label>
        ))}
      </div>
    </Setting>
  )
}

function DailyGoalField({
  value,
  error,
  onChange,
  onInvalid,
}: {
  value: number
  error?: string
  onChange: (minutes: number) => void
  onInvalid: (message: string | undefined) => void
}) {
  const { t } = useTranslation()
  const id = useId()
  const [custom, setCustom] = useState(String(value))
  const [shownValue, setShownValue] = useState(value)
  if (shownValue !== value) {
    // Đồng bộ ô tùy chỉnh khi giá trị đã lưu đổi (ví dụ chọn một mốc có sẵn).
    setShownValue(value)
    setCustom(String(value))
  }

  const commitCustom = () => {
    const minutes = Number(custom)
    if (!Number.isInteger(minutes) || minutes < GOAL_MIN || minutes > GOAL_MAX) {
      onInvalid(t('profile.goal.range', { min: GOAL_MIN, max: GOAL_MAX }))
      return
    }
    onInvalid(undefined)
    if (minutes !== value) onChange(minutes)
  }

  return (
    <Setting
      label={t('profile.goal.label')}
      description={t('profile.goal.description')}
      error={error}
    >
      <div className="flex flex-wrap items-center gap-2" role="group" aria-label={t('profile.goal.label')}>
        {GOAL_PRESETS.map((minutes) => (
          <button
            key={minutes}
            type="button"
            aria-pressed={value === minutes}
            onClick={() => {
              onInvalid(undefined)
              onChange(minutes)
            }}
            className={cn(
              'h-10 rounded-full border-[1.5px] px-4 text-sm font-medium tabular-nums transition-colors',
              value === minutes ? 'border-ink bg-ink text-on-ink' : 'border-line bg-surface hover:border-ink',
            )}
          >
            {t('profile.goal.minutes', { count: minutes })}
          </button>
        ))}
        <span className="flex items-center gap-2">
          <label htmlFor={id} className="sr-only">
            {t('profile.goal.custom')}
          </label>
          <input
            id={id}
            type="number"
            inputMode="numeric"
            min={GOAL_MIN}
            max={GOAL_MAX}
            value={custom}
            onChange={(event) => setCustom(event.target.value)}
            onBlur={commitCustom}
            onKeyDown={(event) => {
              if (event.key === 'Enter') commitCustom()
            }}
            className="h-10 w-20 rounded-field border-[1.5px] border-ink bg-surface px-3 text-sm tabular-nums outline-none focus:border-focus focus:ring-4 focus:ring-focus/15"
          />
          <span className="text-sm text-muted">{t('profile.goal.perDay')}</span>
        </span>
      </div>
    </Setting>
  )
}

function RolloverField({ value, error, onChange }: { value: number; error?: string; onChange: (hour: number) => void }) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const hours = Array.from({ length: 24 }, (_, hour) => hour)
  const label = (hour: number) =>
    new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }).format(
      Date.UTC(2026, 0, 1, hour),
    )
  return (
    <Setting
      label={t('profile.rollover.label')}
      description={t('profile.rollover.description', { time: label(value) })}
      error={error}
      htmlFor={id}
    >
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        data-testid="rollover"
        className="h-12 w-full max-w-xs rounded-field border-[1.5px] border-ink bg-surface px-3 text-[0.9375rem] outline-none focus:border-focus focus:ring-4 focus:ring-focus/15"
      >
        {hours.map((hour) => (
          <option key={hour} value={hour}>
            {label(hour)}
            {hour === 4 ? ` ${t('profile.rollover.default')}` : ''}
          </option>
        ))}
      </select>
    </Setting>
  )
}
