import {
  DOCUMENT_STATUSES,
  DOCUMENT_TYPES,
  type DocumentStatus,
  type DocumentType,
  type LibraryQuery,
  type LibraryScope,
  type LibrarySort,
  type SortDirection,
} from '../types'

const SORTS: LibrarySort[] = ['NAME', 'ADDED', 'OPENED', 'SIZE']
const DATE = /^\d{4}-\d{2}-\d{2}$/

/**
 * Trạng thái xem của kho tài liệu nằm trên địa chỉ trang (NCL-05-CN-001): mở lại, gửi đường dẫn hay bấm Quay lại đều
 * về đúng thư mục và bộ lọc. Ví dụ {@code /library?view=folder&folder=…&type=PPTX&tag=…&sort=NAME}.
 */
export function parseLibraryQuery(params: URLSearchParams): LibraryQuery {
  const view = params.get('view')
  const folderId = params.get('folder')
  let scope: LibraryScope = 'ALL'
  if (folderId || view === 'root') scope = 'FOLDER'
  else if (view === 'recent') scope = 'RECENT'
  else if (view === 'starred') scope = 'STARRED'
  const sort = (params.get('sort') ?? '').toUpperCase() as LibrarySort
  const validSort = SORTS.includes(sort) ? sort : 'ADDED'
  const dir = (params.get('dir') ?? '').toUpperCase()
  const direction: SortDirection =
    dir === 'ASC' || dir === 'DESC' ? dir : validSort === 'NAME' ? 'ASC' : 'DESC'
  const from = params.get('from') ?? undefined
  const to = params.get('to') ?? undefined
  return {
    scope,
    folderId: scope === 'FOLDER' ? folderId : null,
    types: list(params, 'type').filter((value): value is DocumentType =>
      (DOCUMENT_TYPES as string[]).includes(value),
    ),
    tagIds: list(params, 'tag'),
    statuses: list(params, 'status').filter((value): value is DocumentStatus =>
      (DOCUMENT_STATUSES as string[]).includes(value),
    ),
    from: from && DATE.test(from) ? from : undefined,
    to: to && DATE.test(to) ? to : undefined,
    sort: validSort,
    direction,
  }
}

export function toSearchParams(query: LibraryQuery, layout?: string): URLSearchParams {
  const params = new URLSearchParams()
  if (query.scope === 'FOLDER') {
    if (query.folderId) params.set('folder', query.folderId)
    else params.set('view', 'root')
  } else if (query.scope === 'RECENT') params.set('view', 'recent')
  else if (query.scope === 'STARRED') params.set('view', 'starred')
  if (query.types.length) params.set('type', query.types.join(','))
  if (query.tagIds.length) params.set('tag', query.tagIds.join(','))
  if (query.statuses.length) params.set('status', query.statuses.join(','))
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  if (query.sort !== 'ADDED') params.set('sort', query.sort)
  const defaultDirection = query.sort === 'NAME' ? 'ASC' : 'DESC'
  if (query.direction !== defaultDirection) params.set('dir', query.direction)
  if (layout) params.set('layout', layout)
  return params
}

/** Tham số gửi máy chủ. Ngày theo giờ địa phương được đổi ra mốc thời gian: "đến ngày" tính trọn ngày đó. */
export function queryParams(query: LibraryQuery): Record<string, string | string[]> {
  const params: Record<string, string | string[]> = {
    scope: query.scope,
    sort: query.sort,
    direction: query.direction,
  }
  if (query.scope === 'FOLDER' && query.folderId) params.folderId = query.folderId
  if (query.types.length) params.type = query.types
  if (query.tagIds.length) params.tag = query.tagIds
  if (query.statuses.length) params.status = query.statuses
  if (query.from) params.from = startOfLocalDay(query.from).toISOString()
  if (query.to) {
    const end = startOfLocalDay(query.to)
    end.setDate(end.getDate() + 1)
    params.to = end.toISOString()
  }
  return params
}

/** Có bộ lọc nào ngoài phạm vi và cách sắp xếp không. */
export function hasFilters(query: LibraryQuery) {
  return Boolean(query.types.length || query.tagIds.length || query.statuses.length || query.from || query.to)
}

export function clearFilters(query: LibraryQuery): LibraryQuery {
  return { ...query, types: [], tagIds: [], statuses: [], from: undefined, to: undefined }
}

function startOfLocalDay(date: string) {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function list(params: URLSearchParams, key: string) {
  return (params.get(key) ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
}
