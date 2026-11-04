/** Kiểu dữ liệu kéo thả riêng của kho tài liệu, để không lẫn với tệp hay chữ kéo từ nơi khác. */
export const DRAG_DOCUMENTS = 'application/x-learning-companion-documents'
export const DRAG_FOLDER = 'application/x-learning-companion-folder'

export function writeDraggedDocuments(data: DataTransfer, ids: string[]) {
  data.setData(DRAG_DOCUMENTS, JSON.stringify(ids))
  data.effectAllowed = 'move'
}

export function readDraggedDocuments(data: DataTransfer): string[] {
  try {
    const parsed: unknown = JSON.parse(data.getData(DRAG_DOCUMENTS) || '[]')
    return Array.isArray(parsed) ? parsed.filter((value): value is string => typeof value === 'string') : []
  } catch {
    return []
  }
}
