import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { tagSwatch } from '@/features/library/lib/tagColors'
import { TAG_COLORS } from '@/features/library/types'
import { toApiError } from '@/shared/api/errors'
import { cn } from '@/shared/lib/cn'
import { Button, Dialog, TextField, toast } from '@/shared/ui'
import { useWorkspaceMutation } from '../api/queries'
import { workspacesApi } from '../api/workspacesApi'
import {
  WORKSPACE_DESCRIPTION_MAX,
  WORKSPACE_ICONS,
  WORKSPACE_NAME_MAX,
  type Workspace,
  type WorkspaceColor,
  type WorkspaceIcon,
  type WorkspaceInput,
} from '../types'
import { workspaceIcons } from '../lib/icons'
import { WorkspaceGlyph } from './WorkspaceGlyph'

interface CreateWorkspaceDialogProps {
  open: boolean
  /** Có thì sửa không gian học này; không có thì tạo mới. */
  workspace?: Workspace | null
  onClose: () => void
  onSaved?: (workspace: Workspace) => void
}

/**
 * Tạo hoặc sửa không gian học (NCL-06-CN-001): tên bắt buộc tối đa một trăm ký tự, biểu tượng, màu và mô tả. Không giới
 * hạn số không gian học nên hộp thoại không bao giờ chặn vì số lượng.
 */
export function CreateWorkspaceDialog({ open, workspace, onClose, onSaved }: CreateWorkspaceDialogProps) {
  const { t } = useTranslation()
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={workspace ? t('workspaces.form.editTitle') : t('workspaces.form.createTitle')}
      description={workspace ? undefined : t('workspaces.form.createHint')}
      className="w-[min(94vw,560px)]"
    >
      <WorkspaceForm
        workspace={workspace ?? null}
        onCancel={onClose}
        onSaved={(saved) => {
          // Đóng trước rồi mới báo đã lưu: nơi gọi có thể chuyển sang trang của không gian học vừa tạo.
          onClose()
          onSaved?.(saved)
        }}
      />
    </Dialog>
  )
}

function WorkspaceForm({
  workspace,
  onCancel,
  onSaved,
}: {
  workspace: Workspace | null
  onCancel: () => void
  onSaved: (workspace: Workspace) => void
}) {
  const { t, i18n } = useTranslation()
  const [name, setName] = useState(workspace?.name ?? '')
  const [icon, setIcon] = useState<WorkspaceIcon>(workspace?.icon ?? 'BOOK')
  const [color, setColor] = useState<WorkspaceColor>(workspace?.color ?? 'SUN')
  const [description, setDescription] = useState(workspace?.description ?? '')
  const [nameError, setNameError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const nameField = useRef<HTMLInputElement>(null)
  const descriptionId = useId()
  const iconGroup = useId()
  const colorGroup = useId()
  const save = useWorkspaceMutation((input: WorkspaceInput) =>
    workspace ? workspacesApi.update(workspace.id, input) : workspacesApi.create(input),
  )

  // Thẻ <dialog> tự đặt tiêu điểm khi mở, nên ô tên nhận tiêu điểm sau đó một nhịp.
  useEffect(() => {
    const timer = window.setTimeout(() => nameField.current?.focus(), 0)
    return () => window.clearTimeout(timer)
  }, [])

  const trimmed = name.trim().replace(/\s+/g, ' ')
  const tooLong = trimmed.length > WORKSPACE_NAME_MAX
  const descriptionTooLong = description.trim().length > WORKSPACE_DESCRIPTION_MAX
  const fieldError = nameError ?? (tooLong ? t('workspaces.form.nameTooLong') : undefined)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!trimmed) {
      setNameError(t('workspaces.form.nameRequired'))
      nameField.current?.focus()
      return
    }
    if (tooLong || descriptionTooLong) return
    setError(null)
    try {
      const saved = await save.mutateAsync({ name: trimmed, icon, color, description: description.trim() })
      toast.success(
        workspace
          ? t('workspaces.form.updated', { name: saved.name })
          : t('workspaces.form.created', { name: saved.name }),
      )
      onSaved(saved)
    } catch (caught) {
      const apiError = toApiError(caught)
      if (apiError.code === 'VALIDATION_FAILED' && apiError.fieldErrors.some((field) => field.field === 'name')) {
        setNameError(t('workspaces.form.nameTooLong'))
      } else {
        setError(t(`errors.${apiError.code}`, { defaultValue: t('workspaces.actionFailed') }))
      }
    }
  }

  return (
    <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-5" data-testid="workspace-form">
      <div className="flex items-start gap-3">
        <WorkspaceGlyph icon={icon} color={color} size="lg" />
        <TextField
          ref={nameField}
          className="min-w-0 flex-1"
          label={t('workspaces.form.name')}
          value={name}
          maxLength={WORKSPACE_NAME_MAX + 20}
          autoComplete="off"
          onChange={(event) => {
            setName(event.target.value)
            setNameError(null)
          }}
          error={fieldError}
          hint={
            fieldError
              ? undefined
              : t('workspaces.form.nameHint', {
                  used: trimmed.length.toLocaleString(i18n.language),
                  max: WORKSPACE_NAME_MAX.toLocaleString(i18n.language),
                })
          }
          data-testid="workspace-name"
        />
      </div>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium">{t('workspaces.form.icon')}</legend>
        <div className="grid grid-cols-6 gap-2 sm:grid-cols-12">
          {WORKSPACE_ICONS.map((value) => {
            const Icon = workspaceIcons[value]
            return (
              <label
                key={value}
                title={t(`workspaces.icons.${value}`)}
                className={cn(
                  'grid aspect-square min-h-11 cursor-pointer place-items-center rounded-xl border-[1.5px] border-line',
                  'hover:bg-ink/5 has-[:checked]:border-ink has-[:checked]:bg-sun has-[:checked]:text-ink',
                  'has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus',
                )}
              >
                <input
                  type="radio"
                  name={iconGroup}
                  value={value}
                  checked={icon === value}
                  onChange={() => setIcon(value)}
                  aria-label={t(`workspaces.icons.${value}`)}
                  className="sr-only"
                  data-testid={`workspace-icon-${value}`}
                />
                <Icon className="size-5" aria-hidden="true" />
              </label>
            )
          })}
        </div>
      </fieldset>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium">{t('workspaces.form.color')}</legend>
        <div className="flex flex-wrap gap-2">
          {TAG_COLORS.map((value) => (
            <label
              key={value}
              className={cn(
                'flex min-h-11 cursor-pointer items-center gap-2 rounded-xl border-[1.5px] border-line px-3 text-sm',
                'hover:bg-ink/5 has-[:checked]:border-ink has-[:checked]:bg-paper has-[:checked]:font-medium',
                'has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus',
              )}
            >
              <input
                type="radio"
                name={colorGroup}
                value={value}
                checked={color === value}
                onChange={() => setColor(value)}
                className="sr-only"
                data-testid={`workspace-color-${value}`}
              />
              <span
                aria-hidden="true"
                className={cn('size-4 shrink-0 rounded-full border-[1.5px] border-ink', tagSwatch[value])}
              />
              {t(`library.tags.colors.${value}`)}
            </label>
          ))}
        </div>
      </fieldset>

      <div className="flex flex-col gap-1.5">
        <label htmlFor={descriptionId} className="text-sm font-medium">
          {t('workspaces.form.description')}
        </label>
        <textarea
          id={descriptionId}
          value={description}
          rows={3}
          onChange={(event) => setDescription(event.target.value)}
          placeholder={t('workspaces.form.descriptionPlaceholder')}
          aria-invalid={descriptionTooLong ? true : undefined}
          aria-describedby={`${descriptionId}-count`}
          className={cn(
            'w-full resize-y rounded-field border-[1.5px] border-ink bg-surface px-4 py-3 text-[0.9375rem] leading-relaxed',
            'outline-none placeholder:text-subtle focus:border-focus focus:ring-4 focus:ring-focus/15',
            descriptionTooLong && 'border-danger focus:border-danger focus:ring-danger/15',
          )}
          data-testid="workspace-description"
        />
        <p
          id={`${descriptionId}-count`}
          className={cn('text-[0.8125rem] tabular-nums', descriptionTooLong ? 'text-danger' : 'text-muted')}
        >
          {t('workspaces.form.descriptionCount', {
            used: description.trim().length.toLocaleString(i18n.language),
            max: WORKSPACE_DESCRIPTION_MAX.toLocaleString(i18n.language),
          })}
        </p>
      </div>

      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button variant="outline" onClick={onCancel}>
          {t('common.cancel')}
        </Button>
        <Button
          type="submit"
          variant="accent"
          loading={save.isPending}
          disabled={tooLong || descriptionTooLong}
          data-testid="workspace-save"
        >
          {workspace ? t('workspaces.form.save') : t('workspaces.form.create')}
        </Button>
      </div>
    </form>
  )
}
