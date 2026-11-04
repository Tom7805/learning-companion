import { ArrowDownWideNarrow, ArrowUpNarrowWide, LayoutGrid, List, SlidersHorizontal, Tags, X } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui'
import { clearFilters, hasFilters } from '../lib/query'
import {
  DOCUMENT_STATUSES,
  DOCUMENT_TYPES,
  type LibraryQuery,
  type LibrarySort,
  type Tag,
} from '../types'
import { TagDot } from './TagChip'

export type LibraryLayout = 'grid' | 'list'

interface DocumentFiltersProps {
  query: LibraryQuery
  tags: Tag[]
  layout: LibraryLayout
  /** Nhận hàm đổi trạng thái: luôn áp lên trạng thái mới nhất, kể cả khi bấm liên tiếp rất nhanh. */
  onChange: (update: (query: LibraryQuery) => LibraryQuery) => void
  onLayoutChange: (layout: LibraryLayout) => void
  onManageTags: () => void
}

const SORTS: LibrarySort[] = ['ADDED', 'NAME', 'OPENED', 'SIZE']

/**
 * Lọc theo loại tệp, nhãn, trạng thái xử lý và khoảng ngày thêm; sắp xếp theo tên, ngày thêm, lần mở gần nhất hay
 * dung lượng; chuyển dạng lưới và danh sách (NCL-05-CN-001 TC-02). Bộ lọc nằm trong một khối mở ra được để vừa màn
 * hình điện thoại.
 */
export function DocumentFilters({ query, tags, layout, onChange, onLayoutChange, onManageTags }: DocumentFiltersProps) {
  const { t } = useTranslation()
  const panelId = useId()
  const sortId = useId()
  const active = query.types.length + query.tagIds.length + query.statuses.length + (query.from ? 1 : 0) + (query.to ? 1 : 0)
  const [open, setOpen] = useState(active > 0)
  const recent = query.scope === 'RECENT'

  const toggle = <K extends 'types' | 'tagIds' | 'statuses'>(key: K, value: LibraryQuery[K][number]) => {
    onChange((current) => {
      const list = current[key] as string[]
      const next = list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
      return { ...current, [key]: next }
    })
  }

  return (
    <section aria-label={t('library.filters.label')} className="flex flex-col gap-3" data-testid="library-filters">
      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant={open ? 'dark' : 'outline'}
          aria-expanded={open}
          aria-controls={panelId}
          onClick={() => setOpen((value) => !value)}
          icon={<SlidersHorizontal className="size-4" aria-hidden="true" />}
        >
          {t('library.filters.toggle')}
          {active > 0 && (
            <span className="rounded-md bg-sun px-1.5 text-xs font-semibold text-ink tabular-nums">
              <span className="sr-only">: </span>
              {t('library.filters.active', { count: active })}
            </span>
          )}
        </Button>
        {!recent && (
          <div className="flex min-w-0 grow basis-60 items-center gap-1.5 sm:grow-0">
            <label htmlFor={sortId} className="sr-only">
              {t('library.filters.sort')}
            </label>
            <select
              id={sortId}
              value={query.sort}
              onChange={(event) => {
                const sort = event.target.value as LibrarySort
                onChange((current) => ({ ...current, sort, direction: sort === 'NAME' ? 'ASC' : 'DESC' }))
              }}
              className="h-11 w-full min-w-0 truncate rounded-field border-[1.5px] border-ink bg-surface px-3 text-[0.9375rem] text-ink sm:w-auto"
            >
              {SORTS.map((sort) => (
                <option key={sort} value={sort}>
                  {t('library.filters.sort')}: {t(`library.filters.sortBy.${sort}`)}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => onChange((current) => ({ ...current, direction: current.direction === 'ASC' ? 'DESC' : 'ASC' }))}
              aria-label={t('library.filters.toggleDirection', {
                direction: t(`library.filters.direction.${query.direction}`),
              })}
              title={t(`library.filters.direction.${query.direction}`)}
              className="grid size-11 shrink-0 place-items-center rounded-xl border-[1.5px] border-ink bg-surface text-ink hover:bg-paper"
              data-testid="sort-direction"
            >
              {query.direction === 'ASC' ? (
                <ArrowUpNarrowWide className="size-4" aria-hidden="true" />
              ) : (
                <ArrowDownWideNarrow className="size-4" aria-hidden="true" />
              )}
            </button>
          </div>
        )}
        <div role="group" aria-label={t('library.filters.layout')} className="ml-auto flex rounded-xl border-[1.5px] border-ink p-0.5">
          <LayoutButton
            pressed={layout === 'grid'}
            label={t('library.filters.grid')}
            icon={<LayoutGrid className="size-4" aria-hidden="true" />}
            onClick={() => onLayoutChange('grid')}
          />
          <LayoutButton
            pressed={layout === 'list'}
            label={t('library.filters.list')}
            icon={<List className="size-4" aria-hidden="true" />}
            onClick={() => onLayoutChange('list')}
          />
        </div>
      </div>
      {recent && <p className="text-[0.8125rem] text-muted">{t('library.filters.recentNote')}</p>}

      <div
        id={panelId}
        hidden={!open}
        className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4"
      >
        <ChipGroup label={t('library.filters.type')}>
          {DOCUMENT_TYPES.map((type) => (
            <ToggleChip key={type} pressed={query.types.includes(type)} onClick={() => toggle('types', type)}>
              {t(`library.type.${type}`)}
            </ToggleChip>
          ))}
        </ChipGroup>
        <ChipGroup
          label={t('library.filters.tags')}
          action={
            <Button variant="ghost" className="h-9 px-3 text-sm" onClick={onManageTags} icon={<Tags className="size-4" aria-hidden="true" />}>
              {t('library.tags.manage')}
            </Button>
          }
        >
          {tags.length === 0 && <p className="text-sm text-muted">{t('library.filters.noTags')}</p>}
          {tags.map((tag) => (
            <ToggleChip key={tag.id} pressed={query.tagIds.includes(tag.id)} onClick={() => toggle('tagIds', tag.id)}>
              <TagDot tag={tag} />
            </ToggleChip>
          ))}
        </ChipGroup>
        <ChipGroup label={t('library.filters.status')}>
          {DOCUMENT_STATUSES.map((status) => (
            <ToggleChip key={status} pressed={query.statuses.includes(status)} onClick={() => toggle('statuses', status)}>
              {t(`library.status.${status}`)}
            </ToggleChip>
          ))}
        </ChipGroup>
        <div className="flex flex-wrap items-end gap-3">
          <DateInput label={t('library.filters.from')} value={query.from} max={query.to} onChange={(from) => onChange((current) => ({ ...current, from }))} />
          <DateInput label={t('library.filters.to')} value={query.to} min={query.from} onChange={(to) => onChange((current) => ({ ...current, to }))} />
          {hasFilters(query) && (
            <Button variant="ghost" onClick={() => onChange(clearFilters)} icon={<X className="size-4" aria-hidden="true" />}>
              {t('library.filters.clear')}
            </Button>
          )}
        </div>
      </div>
    </section>
  )
}

function LayoutButton({ pressed, label, icon, onClick }: { pressed: boolean; label: string; icon: ReactNode; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      aria-label={label}
      title={label}
      onClick={onClick}
      className={cn(
        'grid size-10 place-items-center rounded-[0.6rem] transition-colors',
        pressed ? 'bg-ink text-on-ink' : 'text-ink hover:bg-ink/5',
      )}
    >
      {icon}
    </button>
  )
}

function ChipGroup({ label, action, children }: { label: string; action?: ReactNode; children: ReactNode }) {
  const id = useId()
  return (
    <div role="group" aria-labelledby={id} className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <span id={id} className="text-sm font-medium">
          {label}
        </span>
        {action}
      </div>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  )
}

function ToggleChip({ pressed, onClick, children }: { pressed: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={cn(
        'inline-flex min-h-9 items-center gap-1.5 rounded-lg border-[1.5px] border-ink px-3 text-sm transition-colors',
        pressed ? 'bg-ink text-on-ink' : 'bg-surface text-ink hover:bg-paper',
      )}
    >
      {children}
    </button>
  )
}

function DateInput({
  label,
  value,
  min,
  max,
  onChange,
}: {
  label: string
  value?: string
  min?: string
  max?: string
  onChange: (value: string | undefined) => void
}) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        type="date"
        value={value ?? ''}
        min={min}
        max={max}
        onChange={(event) => onChange(event.target.value || undefined)}
        className="h-11 rounded-field border-[1.5px] border-ink bg-surface px-3 text-[0.9375rem] text-ink"
      />
    </div>
  )
}
