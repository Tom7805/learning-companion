import { Download, Keyboard, MonitorSmartphone, Palette, PanelLeftClose, PanelRight, Search, Share } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { installPrompt, useInstallState } from '@/app/pwa'
import { routes } from '@/app/routes'
import { Button, toast } from '@/shared/ui'

function Tip({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <li className="flex gap-3">
      <span
        aria-hidden="true"
        className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-xl border-[1.5px] border-ink bg-sky [&_svg]:size-4"
      >
        {icon}
      </span>
      <div className="flex min-w-0 flex-col gap-0.5 text-sm leading-relaxed">
        <p className="font-medium">{title}</p>
        <div className="text-ink-soft">{children}</div>
      </div>
    </li>
  )
}

/**
 * Trợ giúp mở trong khung xem bên (NCL-02-CN-001): cách dùng khung ứng dụng, đổi giao diện và cài ứng dụng lên máy.
 */
export function HelpPanel({ onOpenAppearance }: { onOpenAppearance: () => void }) {
  const { t } = useTranslation()
  const installState = useInstallState()

  const install = async () => {
    if ((await installPrompt.install()) === 'accepted') toast.success(t('shell.installed'))
  }

  return (
    <div className="flex flex-col gap-7" data-testid="help-panel">
      <section className="flex flex-col gap-3">
        <h3 className="font-semibold">{t('help.shellTitle')}</h3>
        <ul className="flex flex-col gap-4">
          <Tip icon={<Search />} title={t('help.quickOpen.title')}>
            {t('help.quickOpen.body')}
          </Tip>
          <Tip icon={<PanelLeftClose />} title={t('help.sidebar.title')}>
            {t('help.sidebar.body')}
          </Tip>
          <Tip icon={<PanelRight />} title={t('help.panel.title')}>
            {t('help.panel.body')}
          </Tip>
          <Tip icon={<Keyboard />} title={t('help.keyboard.title')}>
            {t('help.keyboard.body')}
          </Tip>
        </ul>
      </section>

      <section className="flex flex-col gap-3">
        <h3 className="font-semibold">{t('help.appearance.title')}</h3>
        <p className="text-sm leading-relaxed text-ink-soft">{t('help.appearance.body')}</p>
        <Button variant="outline" className="self-start" onClick={onOpenAppearance} icon={<Palette className="size-4" aria-hidden="true" />}>
          {t('help.appearance.action')}
        </Button>
      </section>

      <section className="flex flex-col gap-3" data-testid="help-install">
        <h3 className="flex items-center gap-2 font-semibold">
          <MonitorSmartphone className="size-4" aria-hidden="true" />
          {t('help.install.title')}
        </h3>
        {installState === 'installed' && <p className="text-sm text-ink-soft">{t('help.install.installed')}</p>}
        {installState === 'available' && (
          <>
            <p className="text-sm leading-relaxed text-ink-soft">{t('help.install.available')}</p>
            <Button variant="dark" className="self-start" onClick={() => void install()} icon={<Download className="size-4" aria-hidden="true" />}>
              {t('shell.install')}
            </Button>
          </>
        )}
        {installState === 'unavailable' && (
          <p className="flex items-start gap-2 text-sm leading-relaxed text-ink-soft">
            <Share className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {t('help.install.manual')}
          </p>
        )}
      </section>

      <p className="text-[0.8125rem] text-muted">
        <Link to={routes.terms} target="_blank" className="font-medium underline underline-offset-4">
          {t('legal.terms')}
        </Link>
        {' · '}
        <Link to={routes.privacy} target="_blank" className="font-medium underline underline-offset-4">
          {t('legal.privacy')}
        </Link>
      </p>
    </div>
  )
}
