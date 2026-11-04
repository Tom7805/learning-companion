import type { Folder } from '../types'

export interface FolderNode extends Folder {
  children: FolderNode[]
  depth: number
  /** Số tài liệu trong thư mục này cộng mọi thư mục con cháu. */
  totalCount: number
}

const collator = new Intl.Collator('vi', { sensitivity: 'base', numeric: true })

/** Dựng cây từ danh sách phẳng; thư mục anh em xếp theo tên tiếng Việt (số so theo giá trị: "Chương 2" trước "Chương 10"). */
export function buildTree(folders: Folder[]): FolderNode[] {
  const nodes = new Map<string, FolderNode>()
  for (const folder of folders) nodes.set(folder.id, { ...folder, children: [], depth: 0, totalCount: 0 })
  const roots: FolderNode[] = []
  for (const node of nodes.values()) {
    const parent = node.parentId ? nodes.get(node.parentId) : undefined
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  const finish = (list: FolderNode[], depth: number): number => {
    list.sort((a, b) => collator.compare(a.name, b.name))
    let sum = 0
    for (const node of list) {
      node.depth = depth
      node.totalCount = node.documentCount + finish(node.children, depth + 1)
      sum += node.totalCount
    }
    return sum
  }
  finish(roots, 0)
  return roots
}

/** Đường dẫn từ gốc xuống tới thư mục (kể cả nó), cho dòng định vị và khung thông tin. */
export function folderPath(folders: Folder[], folderId: string | null | undefined): Folder[] {
  const byId = new Map(folders.map((folder) => [folder.id, folder]))
  const path: Folder[] = []
  let next = folderId ? byId.get(folderId) : undefined
  while (next && path.length < 64) {
    path.unshift(next)
    next = next.parentId ? byId.get(next.parentId) : undefined
  }
  return path
}

/** Mã của thư mục và mọi thư mục con cháu: không được chuyển thư mục vào những chỗ này. */
export function descendantIds(folders: Folder[], folderId: string): Set<string> {
  const children = new Map<string, string[]>()
  for (const folder of folders) {
    if (folder.parentId) children.set(folder.parentId, [...(children.get(folder.parentId) ?? []), folder.id])
  }
  const found = new Set<string>()
  const queue = [folderId]
  while (queue.length) {
    const id = queue.pop()!
    if (found.has(id)) continue
    found.add(id)
    queue.push(...(children.get(id) ?? []))
  }
  return found
}

/** Danh sách phẳng theo thứ tự cây (cha trước con), cho hộp thoại chọn thư mục đích. */
export function flatten(nodes: FolderNode[]): FolderNode[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children)])
}
