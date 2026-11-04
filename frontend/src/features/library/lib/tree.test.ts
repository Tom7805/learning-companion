import { describe, expect, it } from 'vitest'
import type { Folder } from '../types'
import { buildTree, descendantIds, flatten, folderPath } from './tree'

const folder = (id: string, name: string, parentId: string | null, documentCount = 0): Folder => ({
  id,
  name,
  parentId,
  documentCount,
  createdAt: '2026-10-01T00:00:00Z',
})

const folders = [
  folder('hk1', 'Học kỳ một', null, 1),
  folder('c10', 'Chương 10', 'hk1', 2),
  folder('c2', 'Chương 2', 'hk1', 3),
  folder('c2a', 'Bài tập', 'c2', 4),
  folder('an', 'Anh văn', null),
]

describe('cây thư mục', () => {
  it('dựng cây, xếp tên tiếng Việt theo số và cộng số tài liệu của cả cây con', () => {
    const roots = buildTree(folders)
    expect(roots.map((node) => node.name)).toEqual(['Anh văn', 'Học kỳ một'])
    const semester = roots[1]
    expect(semester.children.map((node) => node.name)).toEqual(['Chương 2', 'Chương 10'])
    expect(semester.totalCount).toBe(10)
    expect(semester.children[0].children[0]).toMatchObject({ name: 'Bài tập', depth: 2, totalCount: 4 })
    expect(flatten(roots).map((node) => node.id)).toEqual(['an', 'hk1', 'c2', 'c2a', 'c10'])
  })

  it('đường dẫn từ gốc và mọi thư mục con cháu', () => {
    expect(folderPath(folders, 'c2a').map((node) => node.name)).toEqual(['Học kỳ một', 'Chương 2', 'Bài tập'])
    expect(folderPath(folders, null)).toEqual([])
    expect([...descendantIds(folders, 'hk1')].sort()).toEqual(['c10', 'c2', 'c2a', 'hk1'])
  })

  it('thư mục cha không còn (đã vào thùng rác) thì thư mục con nằm ở gốc', () => {
    expect(buildTree([folder('x', 'Mồ côi', 'gone')]).map((node) => node.name)).toEqual(['Mồ côi'])
  })
})
