import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Checkbox, Dialog, TextField } from '@/shared/ui'
import type { PlacedTopic } from '../lib/topics'
import type { TopicNode } from '../types'

const TITLE_MAX = 200
const GOAL_MAX = 500

/**
 * Hộp thoại thêm hay sửa một chủ đề của dàn ý (NCL-06-CN-002): tên bắt buộc tối đa 200 ký tự, mục tiêu học không bắt
 * buộc tối đa 500 ký tự. Lỗi kiểm tra hiện ngay dưới ô nhập.
 */
export function TopicEditor({
  open,
  topic,
  parent,
  busy,
  onSubmit,
  onClose,
}: {
  open: boolean
  /** Chủ đề đang sửa; không có thì là thêm mới. */
  topic?: TopicNode | null
  /** Chủ đề cha khi thêm chủ đề con. */
  parent?: TopicNode | null
  busy: boolean
  onSubmit: (input: { title: string; goal: string }) => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [title, setTitle] = useState(topic?.title ?? '')
  const [goal, setGoal] = useState(topic?.goal ?? '')
  const [touched, setTouched] = useState(false)
  const trimmed = title.trim()
  const titleError = !trimmed
    ? t('workspaces.outline.editor.titleRequired')
    : trimmed.length > TITLE_MAX
      ? t('workspaces.outline.editor.titleTooLong')
      : undefined
  const goalError = goal.trim().length > GOAL_MAX ? t('workspaces.outline.editor.goalTooLong') : undefined

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (titleError || goalError) return
    onSubmit({ title: trimmed, goal: goal.trim() })
  }

  const heading = topic
    ? t('workspaces.outline.editor.editTitle')
    : parent
      ? t('workspaces.outline.editor.createChildTitle', { parent: parent.title })
      : t('workspaces.outline.editor.createTitle')

  return (
    <Dialog open={open} title={heading} onClose={onClose}>
      <form className="flex flex-col gap-4" onSubmit={submit} noValidate data-testid="topic-editor">
        <TextField
          label={t('workspaces.outline.editor.titleLabel')}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          error={touched ? titleError : undefined}
          maxLength={TITLE_MAX + 20}
          autoFocus
          data-testid="topic-title-input"
        />
        <TextField
          label={t('workspaces.outline.editor.goalLabel')}
          value={goal}
          placeholder={t('workspaces.outline.editor.goalPlaceholder')}
          onChange={(event) => setGoal(event.target.value)}
          error={touched ? goalError : undefined}
          data-testid="topic-goal-input"
        />
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="accent" loading={busy} data-testid="topic-save">
            {t('workspaces.outline.editor.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

/** Gộp chủ đề vào một chủ đề khác (không phải chính nó hay chủ đề con của nó). */
export function MergeTopicDialog({
  topic,
  candidates,
  busy,
  onSubmit,
  onClose,
}: {
  topic: TopicNode
  candidates: PlacedTopic[]
  busy: boolean
  onSubmit: (intoId: string) => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [target, setTarget] = useState(candidates[0]?.node.id ?? '')
  return (
    <Dialog
      open
      title={t('workspaces.outline.mergeDialog.title', { title: topic.title })}
      description={t('workspaces.outline.mergeDialog.body')}
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button variant="accent" loading={busy} disabled={!target} onClick={() => onSubmit(target)} data-testid="topic-merge-confirm">
            {t('workspaces.outline.mergeDialog.confirm')}
          </Button>
        </>
      }
    >
      <label className="flex flex-col gap-1.5 text-sm font-medium">
        {t('workspaces.outline.mergeDialog.target')}
        <select
          value={target}
          onChange={(event) => setTarget(event.target.value)}
          className="min-h-11 rounded-field border-[1.5px] border-ink bg-field px-3 text-[0.9375rem]"
          data-testid="topic-merge-target"
        >
          {candidates.map(({ node, depth }) => (
            <option key={node.id} value={node.id}>
              {'— '.repeat(depth - 1)}
              {node.title}
            </option>
          ))}
        </select>
      </label>
    </Dialog>
  )
}

/** Tách chủ đề: chủ đề mới cùng cấp ngay sau, mang theo các chủ đề con được chọn. */
export function SplitTopicDialog({
  topic,
  busy,
  onSubmit,
  onClose,
}: {
  topic: TopicNode
  busy: boolean
  onSubmit: (input: { title: string; childIds: string[] }) => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [title, setTitle] = useState('')
  const [chosen, setChosen] = useState<Set<string>>(() => new Set())
  const [touched, setTouched] = useState(false)
  const error = !title.trim() ? t('workspaces.outline.editor.titleRequired') : undefined
  const toggle = (id: string) =>
    setChosen((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (error) return
    onSubmit({ title: title.trim(), childIds: [...chosen] })
  }
  return (
    <Dialog open title={t('workspaces.outline.splitDialog.title', { title: topic.title })} description={t('workspaces.outline.splitDialog.body')} onClose={onClose}>
      <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
        <TextField
          label={t('workspaces.outline.splitDialog.newTitle')}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          error={touched ? error : undefined}
          autoFocus
          data-testid="topic-split-title"
        />
        {topic.children.length > 0 && (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('workspaces.outline.splitDialog.children')}</legend>
            {topic.children.map((child) => (
              <Checkbox key={child.id} label={child.title} checked={chosen.has(child.id)} onChange={() => toggle(child.id)} />
            ))}
          </fieldset>
        )}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="accent" loading={busy} data-testid="topic-split-confirm">
            {t('workspaces.outline.splitDialog.confirm')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
