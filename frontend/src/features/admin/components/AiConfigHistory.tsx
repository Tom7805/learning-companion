import { FlaskConical, GitCompare, Pencil, Rocket } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { formatDateTime } from '@/shared/lib/date'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui'
import type { AiConfigActor, AiConfigStatus, AiConfigVersion } from '../types'

interface AiConfigHistoryProps {
  versions: AiConfigVersion[]
  onEdit: (version: AiConfigVersion) => void
  onTrial: (version: AiConfigVersion) => void
  onPublish: (version: AiConfigVersion) => void
  onCompare: (version: AiConfigVersion) => void
}

const statusClass: Record<AiConfigStatus, string> = {
  PUBLISHED: 'border-ink bg-sun text-ink',
  DRAFT: 'border-ink bg-surface text-ink',
  SUPERSEDED: 'border-line bg-paper text-ink-soft',
  REVOKED: 'border-danger bg-danger-soft text-ink',
}

export function AiConfigStatusBadge({ status }: { status: AiConfigStatus }) {
  const { t } = useTranslation()
  return (
    <span
      className={cn(
        'inline-flex h-6 items-center rounded-full border-[1.5px] px-2.5 text-xs font-semibold',
        statusClass[status],
      )}
      data-testid="ai-config-status"
    >
      {t(`aiConfig.status.${status}`)}
    </span>
  )
}

/** Lịch sử mọi phiên bản của một chức năng, mới nhất trước: ai lưu, ai phát hành, ai thu hồi và lúc nào (TC-05). */
export function AiConfigHistory({ versions, onEdit, onTrial, onPublish, onCompare }: AiConfigHistoryProps) {
  const { t, i18n } = useTranslation()
  const id = useId()
  const timeZone = useDisplayTimeZone()
  const published = versions.find((version) => version.status === 'PUBLISHED')
  const who = (actor?: AiConfigActor | null) =>
    actor ? (actor.displayName ?? t('aiConfig.history.unknownActor')) : t('aiConfig.history.system')
  const when = (value: string) => formatDateTime(value, i18n.language, timeZone)

  return (
    <section aria-labelledby={`${id}-title`} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 id={`${id}-title`} className="text-xl font-semibold">
          {t('aiConfig.history.title')}
        </h2>
        <p className="text-sm text-muted">{t('aiConfig.history.subtitle')}</p>
      </div>
      <ol
        className="divide-y divide-line rounded-card border-[1.5px] border-ink bg-surface px-4 sm:px-5"
        data-testid="ai-config-history"
      >
        {versions.map((version) => {
          const name = t('aiConfig.versionName', { version: version.versionNumber })
          const publishable =
            version.status === 'SUPERSEDED' || (version.status === 'DRAFT' && Boolean(version.lastTrialAt))
          return (
            <li
              key={version.id}
              className="flex flex-col gap-3 py-4 xl:flex-row xl:items-start xl:justify-between"
              data-testid={`ai-config-version-${version.versionNumber}`}
            >
              <div className="flex min-w-0 flex-col gap-1.5">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="font-semibold">{name}</h3>
                  <AiConfigStatusBadge status={version.status} />
                  {version.basedOnVersion != null && (
                    <span className="text-[0.8125rem] text-muted">
                      {t('aiConfig.history.basedOn', { version: version.basedOnVersion })}
                    </span>
                  )}
                </div>
                <p className="text-sm text-ink-soft">
                  {t('aiConfig.summary', {
                    provider: version.providerCode ?? t('aiConfig.editor.followRoute'),
                    model: version.model ?? t('aiConfig.defaultModel'),
                    temperature: version.temperature,
                    tokens: version.maxOutputTokens,
                  })}
                </p>
                <ul className="flex flex-col gap-0.5 text-[0.8125rem] text-muted">
                  <li>{t('aiConfig.history.created', { who: who(version.createdBy), time: when(version.createdAt) })}</li>
                  {version.lastTrialAt && <li>{t('aiConfig.history.tried', { time: when(version.lastTrialAt) })}</li>}
                  {version.publishedAt && (
                    <li data-testid="ai-config-published-by">
                      {t(version.status === 'PUBLISHED' ? 'aiConfig.history.published' : 'aiConfig.history.wasPublished', {
                        who: who(version.publishedBy),
                        time: when(version.publishedAt),
                      })}
                    </li>
                  )}
                  {version.revokedAt && (
                    <li className="text-danger">
                      {t('aiConfig.history.revoked', { who: who(version.revokedBy), time: when(version.revokedAt) })}
                    </li>
                  )}
                </ul>
              </div>
              <div className="flex shrink-0 flex-wrap gap-2">
                <Button
                  variant="ghost"
                  className="h-9 px-3 text-sm"
                  icon={<Pencil className="size-4" aria-hidden="true" />}
                  aria-label={t('aiConfig.history.editLabel', { version: version.versionNumber })}
                  onClick={() => onEdit(version)}
                >
                  {t('aiConfig.history.edit')}
                </Button>
                {version.status !== 'REVOKED' && (
                  <Button
                    variant="ghost"
                    className="h-9 px-3 text-sm"
                    icon={<FlaskConical className="size-4" aria-hidden="true" />}
                    aria-label={t('aiConfig.history.trialLabel', { version: version.versionNumber })}
                    onClick={() => onTrial(version)}
                  >
                    {t('aiConfig.history.trial')}
                  </Button>
                )}
                {published && published.id !== version.id && (
                  <Button
                    variant="ghost"
                    className="h-9 px-3 text-sm"
                    icon={<GitCompare className="size-4" aria-hidden="true" />}
                    aria-label={t('aiConfig.history.compareLabel', { version: version.versionNumber })}
                    onClick={() => onCompare(version)}
                  >
                    {t('aiConfig.history.compare')}
                  </Button>
                )}
                {publishable && (
                  <Button
                    variant="outline"
                    className="h-9 px-3 text-sm"
                    icon={<Rocket className="size-4" aria-hidden="true" />}
                    onClick={() => onPublish(version)}
                  >
                    {t(version.status === 'SUPERSEDED' ? 'aiConfig.publish.again' : 'aiConfig.publish.action', {
                      version: version.versionNumber,
                    })}
                  </Button>
                )}
              </div>
            </li>
          )
        })}
      </ol>
    </section>
  )
}
