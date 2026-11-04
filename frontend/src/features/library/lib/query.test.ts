import { describe, expect, it } from 'vitest'
import { hasFilters, parseLibraryQuery, queryParams, toSearchParams } from './query'

describe('trạng thái xem kho tài liệu trên địa chỉ trang', () => {
  it('mặc định là mọi tài liệu, mới thêm trước', () => {
    expect(parseLibraryQuery(new URLSearchParams())).toEqual({
      scope: 'ALL',
      folderId: null,
      types: [],
      tagIds: [],
      statuses: [],
      from: undefined,
      to: undefined,
      sort: 'ADDED',
      direction: 'DESC',
    })
  })

  it('đọc và ghi lại đúng thư mục, bộ lọc, cách sắp xếp; bỏ qua giá trị lạ', () => {
    const query = parseLibraryQuery(
      new URLSearchParams('folder=f1&type=PPTX,BOGUS&tag=t1,t2&status=FAILED&from=2026-10-01&to=bad&sort=name'),
    )
    expect(query).toMatchObject({
      scope: 'FOLDER',
      folderId: 'f1',
      types: ['PPTX'],
      tagIds: ['t1', 't2'],
      statuses: ['FAILED'],
      from: '2026-10-01',
      to: undefined,
      sort: 'NAME',
      direction: 'ASC',
    })
    expect(toSearchParams(query).toString()).toBe(
      'folder=f1&type=PPTX&tag=t1%2Ct2&status=FAILED&from=2026-10-01&sort=NAME',
    )
    expect(hasFilters(query)).toBe(true)
  })

  it('thư mục gốc, gần đây và có gắn sao', () => {
    expect(parseLibraryQuery(new URLSearchParams('view=root'))).toMatchObject({ scope: 'FOLDER', folderId: null })
    expect(parseLibraryQuery(new URLSearchParams('view=recent')).scope).toBe('RECENT')
    expect(toSearchParams(parseLibraryQuery(new URLSearchParams('view=starred&dir=ASC'))).toString()).toBe(
      'view=starred&dir=ASC',
    )
  })

  it('tham số gửi máy chủ: danh sách giữ dạng mảng, ngày đổi ra mốc thời gian địa phương', () => {
    const params = queryParams(parseLibraryQuery(new URLSearchParams('view=root&type=PDF,DOCX&to=2026-12-31')))
    expect(params).toEqual({
      scope: 'FOLDER',
      sort: 'ADDED',
      direction: 'DESC',
      type: ['PDF', 'DOCX'],
      to: new Date(2027, 0, 1).toISOString(),
    })
  })
})
