import { Check, Download, Monitor, Moon, Sun } from 'lucide-react'
import { useId, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useEffectiveTheme } from '@/app/providers/appearance'
import { installPrompt, useInstallState } from '@/app/pwa'
import { useUiPreferences, type FontSize, type ThemeMode } from '@/app/store/uiStore'
import { useAuth } from '@/features/auth'
import { cn } from '@/shared/lib/cn'
import { Button, toast } from '@/shared/ui'
import { useUpdateUiPreferences } from '../api/queries'

const THEMES: { value: ThemeMode; icon: typeof Sun }[] = [
  { value: 'light', icon: Sun },
  { value: 'dark', icon: Moon },
  { value: 'system', icon: Monitor },
]

const FONT_SIZES: { value: FontSize; sample: string }[] = [
  { value: 'small', sample: 'text-sm' },
  { value: 'medium', sample: 'text-base' },
  { value: 'large', sample: 'text-xl' },
]

/** Ô xem trước nhỏ của từng chế độ màu; "theo hệ thống" là nửa sáng nửa tối. */
function ThemePreview({ mode }: { mode: ThemeMode }) {
  const pane = (dark: boolean) => (
    <span className={cn('flex flex-1 flex-col gap-1 p-2', dark ? 'bg-[#1c1c1f]' : 'bg-white')}>
      <span className={cn('h-1.5 w-3/4 rounded-full', dark ? 'bg-[#ededf0]' : 'bg-[#1d1d1f]')} />
      <span className={cn('h-1.5 w-1/2 rounded-full', dark ? 'bg-[#5c4a10]' : 'bg-[#ffd45c]')} />
    </span>
  )
  return (
    <span aria-hidden="true" className="flex h-14 overflow-hidden rounded-xl border-[1.5px] border-ink">
      {mode !== 'dark' && pane(false)}
      {mode !== 'light' && pane(true)}
    </span>
  )
}

function Choice({ name, value, checked, onChange, children, testId }: {
  name: string
  value: string
  checked: boolean
  onChange: () => void
  children: ReactNode
  testId: string
}) {
  return (
    <label
      data-testid={testId}
      className={cn(
        'relative flex cursor-pointer flex-col gap-2 rounded-card border-[1.5px] p-3 transition-colors',
        'has-[:focus-visible]:ring-4 has-[:focus-visible]:ring-focus/30',
        checked ? 'border-ink bg-sun-soft' : 'border-line bg-surface hover:border-ink',
      )}
    >
      <input type="radio" name={name} value={value} checked={checked} onChange={onChange} className="sr-only" />
      {checked && (
        <span
          aria-hidden="true"
          className="absolute -right-2 -top-2 grid size-6 place-items-center rounded-full border-[1.5px] border-ink bg-sun text-ink"
        >
          <Check className="size-3.5" strokeWidth={3} />
        </span>
      )}
      {children}
    </label>
  )
}

/**
 * Mục Giao diện trong cài đặt (NCL-02-CN-001): chế độ sáng, tối hoặc theo hệ thống, ba cỡ chữ và cài ứng dụng lên
 * máy. Thay đổi áp ngay trên máy này và được lưu theo tài khoản để máy khác cũng thấy.
 */
export function AppearanceSection() {
  const { t } = useTranslation()
  const { account } = useAuth()
  const preferences = useUiPreferences()
  const effective = useEffectiveTheme()
  const update = useUpdateUiPreferences(account?.id)
  const installState = useInstallState()
  const themeLabel = useId()
  const fontLabel = useId()

  const change = (changes: Parameters<typeof update.mutate>[0]) =>
    update.mutate(changes, { onError: () => toast.error(t('appearance.saveFailed')) })

  const install = async () => {
    if ((await installPrompt.install()) === 'accepted') toast.success(t('shell.installed'))
  }

  return (
    <div className="flex flex-col gap-8" data-testid="appearance-section">
      <header className="flex flex-col gap-1 pr-10">
        <h2 className="text-2xl tracking-tight">{t('appearance.title')}</h2>
        <p className="text-sm text-muted">{t('appearance.subtitle')}</p>
      </header>

      <section className="flex flex-col gap-3" role="radiogroup" aria-labelledby={themeLabel}>
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h3 id={themeLabel} className="text-lg font-semibold">
            {t('appearance.theme.label')}
          </h3>
          {preferences.themeMode === 'system' && (
            <p className="text-sm text-muted" data-testid="theme-effective">
              {t('appearance.theme.following', { mode: t(`appearance.theme.${effective}`) })}
            </p>
          )}
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          {THEMES.map((theme) => (
            <Choice
              key={theme.value}
              name="theme"
              value={theme.value}
              testId={`theme-${theme.value}`}
              checked={preferences.themeMode === theme.value}
              onChange={() => change({ themeMode: theme.value })}
            >
              <ThemePreview mode={theme.value} />
              <span className="flex items-center gap-2 font-medium">
                <theme.icon className="size-4" aria-hidden="true" />
                {t(`appearance.theme.${theme.value}`)}
              </span>
              <span className="text-[0.8125rem] leading-snug text-muted">
                {t(`appearance.theme.${theme.value}Hint`)}
              </span>
            </Choice>
          ))}
        </div>
      </section>

      <section className="flex flex-col gap-3" role="radiogroup" aria-labelledby={fontLabel}>
        <h3 id={fontLabel} className="text-lg font-semibold">
          {t('appearance.font.label')}
        </h3>
        <div className="grid grid-cols-3 gap-3">
          {FONT_SIZES.map((size) => (
            <Choice
              key={size.value}
              name="font-size"
              value={size.value}
              testId={`font-${size.value}`}
              checked={preferences.fontSize === size.value}
              onChange={() => change({ fontSize: size.value })}
            >
              <span aria-hidden="true" className={cn('font-semibold', size.sample)}>
                Aa
              </span>
              <span className="text-sm font-medium">{t(`appearance.font.${size.value}`)}</span>
            </Choice>
          ))}
        </div>
        <p className="rounded-field bg-paper px-4 py-3 text-[0.9375rem] leading-relaxed text-ink-soft">
          {t('appearance.font.preview')}
        </p>
      </section>

      <section className="flex flex-col gap-3" data-testid="appearance-install">
        <h3 className="text-lg font-semibold">{t('help.install.title')}</h3>
        {installState === 'installed' && <p className="text-sm text-ink-soft">{t('help.install.installed')}</p>}
        {installState === 'available' && (
          <>
            <p className="text-sm leading-relaxed text-ink-soft">{t('help.install.available')}</p>
            <Button
              variant="dark"
              className="self-start"
              onClick={() => void install()}
              icon={<Download className="size-4" aria-hidden="true" />}
            >
              {t('shell.install')}
            </Button>
          </>
        )}
        {installState === 'unavailable' && (
          <p className="text-sm leading-relaxed text-ink-soft">{t('help.install.manual')}</p>
        )}
      </section>
    </div>
  )
}
