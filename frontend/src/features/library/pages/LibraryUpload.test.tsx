import { createEvent, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { documentPath, routes } from '@/app/routes'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { DocumentCard, FolderTree, StartUploadRequest, UploadProgress } from '../types'
import { LibraryPage } from './LibraryPage'

const SEMESTER = '00000000-0000-4000-8000-0000000000a1'
const CALCULUS = '00000000-0000-4000-8000-0000000000a3'
const EXISTING = '00000000-0000-4000-8000-0000000000d1'

const tree: FolderTree = {
  folders: [
    { id: SEMESTER, parentId: null, name: 'Học kỳ một', documentCount: 0, createdAt: '2026-10-01T00:00:00Z' },
    { id: CALCULUS, parentId: SEMESTER, name: 'Giải tích', documentCount: 0, createdAt: '2026-10-01T00:00:00Z' },
  ],
  rootDocumentCount: 0,
  totalDocumentCount: 1,
  starredCount: 0,
}

let started: StartUploadRequest[] = []
let parts: string[] = []
let completed: string[] = []

/** Máy chủ giả: mỗi tệp một lần tải, một phần; tên tệp quyết định kết quả khi hoàn tất. */
function mockUploads() {
  const sessions = new Map<string, StartUploadRequest>()
  server.use(
    http.get('*/api/v1/library/folders', () => HttpResponse.json(tree)),
    http.get('*/api/v1/library/documents', () => HttpResponse.json([])),
    http.post('*/api/v1/library/uploads', async ({ request }) => {
      const body = (await request.json()) as StartUploadRequest
      started.push(body)
      const id = `up-${started.length}`
      sessions.set(id, body)
      return HttpResponse.json(progress(id, body, 0), { status: 201 })
    }),
    http.put('*/api/v1/library/uploads/:id/parts/:index', async ({ params, request }) => {
      const id = String(params.id)
      parts.push(`${id}:${String(params.index)}:${(await request.arrayBuffer()).byteLength}`)
      const body = sessions.get(id)!
      return HttpResponse.json(progress(id, body, body.sizeBytes))
    }),
    http.post('*/api/v1/library/uploads/:id/complete', ({ params }) => {
      const id = String(params.id)
      completed.push(id)
      const body = sessions.get(id)!
      if (body.fileName.startsWith('virus')) return apiError(422, 'UPLOAD_REJECTED', undefined, { reason: 'MALWARE' })
      if (body.fileName.startsWith('khoa')) {
        return apiError(422, 'UPLOAD_REJECTED', undefined, { reason: 'PASSWORD_PROTECTED' })
      }
      if (body.fileName.startsWith('giao-trinh')) {
        return apiError(409, 'LIBRARY_DOCUMENT_DUPLICATE', undefined, {
          documentId: EXISTING,
          title: 'Giáo trình chương 1',
          folderId: CALCULUS,
          folderPath: ['Học kỳ một', 'Giải tích'],
        })
      }
      return HttpResponse.json(document(id, body))
    }),
  )
}

function progress(id: string, body: StartUploadRequest, received: number): UploadProgress {
  return {
    id,
    fileName: body.fileName,
    folderId: body.folderId,
    sizeBytes: body.sizeBytes,
    chunkSize: 2 * 1024 * 1024,
    partCount: 1,
    receivedBytes: received,
    nextPart: received >= body.sizeBytes ? 1 : 0,
    status: 'UPLOADING',
    expiresAt: '2026-11-05T00:00:00Z',
  }
}

function document(id: string, body: StartUploadRequest): DocumentCard {
  return {
    id: `doc-${id}`,
    title: body.fileName.replace(/\.[^.]+$/, ''),
    type: 'PDF',
    originalFilename: body.fileName,
    sizeBytes: body.sizeBytes,
    createdAt: '2026-11-04T03:00:00Z',
    folderId: body.folderId,
    status: 'PENDING',
  }
}

function pdf(name: string, size = 1200) {
  return new File([new Uint8Array(size).fill(37)], name, { type: 'application/pdf' })
}

function renderFolder(folderId = CALCULUS) {
  return renderWithProviders(<LibraryPage />, {
    path: routes.library,
    initialEntries: [`${routes.library}?folder=${folderId}`],
    extraRoutes: [{ path: routes.libraryDocument, element: <p>Trang tài liệu</p> }],
  })
}

beforeEach(() => {
  started = []
  parts = []
  completed = []
  mockUploads()
})

describe('Tải tài liệu lên kho (NCL-05-CN-002)', () => {
  it('TC-01: chọn ba bài giảng trong thư mục Giải tích: cả ba vào đúng thư mục, mỗi tệp có tiến độ và kết quả', async () => {
    const { user } = renderFolder()
    const dropzone = await screen.findByTestId('upload-dropzone')
    await waitFor(() => expect(dropzone).toHaveTextContent('để tải vào Giải tích'))
    expect(dropzone).toHaveTextContent('tối đa 50 MB mỗi tệp')

    await user.upload(screen.getByTestId('upload-input'), [pdf('Bài 1.pdf'), pdf('Bài 2.pdf'), pdf('Bài 3.pdf')])

    const queue = await screen.findByTestId('upload-queue')
    await waitFor(() => expect(within(queue).getAllByTestId('upload-item').map((item) => item.dataset.state)).toEqual(['done', 'done', 'done']))
    expect(started.map((request) => request.folderId)).toEqual([CALCULUS, CALCULUS, CALCULUS])
    const firstIndex = started.findIndex((request) => request.fileName === 'Bài 1.pdf')
    expect(firstIndex).toBeGreaterThanOrEqual(0)
    expect(started[firstIndex]).toMatchObject({ fileName: 'Bài 1.pdf', sizeBytes: 1200 })
    // Dấu vân tay gửi kèm để máy chủ báo trùng trước khi nhận cả tệp.
    expect(started[firstIndex].sha256).toMatch(/^[0-9a-f]{64}$/)
    expect(parts).toHaveLength(3)
    expect(parts).toContain(`up-${firstIndex + 1}:0:1200`)
    expect(within(queue).getByRole('heading')).toHaveTextContent('Đã tải xong 3/3 tệp')
    expect(within(queue).getAllByTestId('upload-status')[0]).toHaveTextContent('Đã thêm vào Giải tích, đang chờ xử lý')
    expect(within(queue).getByRole('link', { name: 'Bài 1' })).toHaveAttribute('href', documentPath(`doc-up-${firstIndex + 1}`))
    expect(screen.getByTestId('upload-announcement')).toHaveTextContent(/Đã tải Bài \d vào Giải tích/)
  })

  it('kéo thả tệp từ máy tính vào trang: vùng kéo thả sáng lên rồi tệp vào thư mục đang mở', async () => {
    renderFolder()
    const main = await screen.findByTestId('library-main')
    await waitFor(() => expect(screen.getByTestId('upload-dropzone')).toHaveTextContent('Giải tích'))
    const files = [pdf('Kéo thả.pdf')]
    const dataTransfer = { files, types: ['Files'], dropEffect: 'none' }

    const over = createEvent.dragOver(main)
    Object.defineProperty(over, 'dataTransfer', { value: dataTransfer })
    fireEvent(main, over)
    expect(screen.getByTestId('upload-dropzone')).toHaveAttribute('data-active', 'true')
    expect(screen.getByTestId('upload-dropzone')).toHaveTextContent('Thả tệp để tải vào Giải tích')

    const drop = createEvent.drop(main)
    Object.defineProperty(drop, 'dataTransfer', { value: dataTransfer })
    fireEvent(main, drop)
    await waitFor(() => expect(screen.getByTestId('upload-item')).toHaveAttribute('data-state', 'done'))
    expect(started[0]).toMatchObject({ fileName: 'Kéo thả.pdf', folderId: CALCULUS })
    expect(screen.getByTestId('upload-dropzone')).not.toHaveAttribute('data-active')
  })

  it('TC-02: tệp có mã độc hay có mật khẩu bị từ chối và nói rõ lý do', async () => {
    const { user } = renderFolder()
    await user.upload(await screen.findByTestId('upload-input'), [pdf('virus.pdf'), pdf('khoa.pdf')])

    await waitFor(() =>
      expect(screen.getAllByTestId('upload-item').map((item) => item.dataset.state)).toEqual(['rejected', 'rejected']),
    )
    const [malware, locked] = screen.getAllByTestId('upload-status')
    expect(malware).toHaveTextContent('Tệp có mã độc nên không được lưu.')
    expect(locked).toHaveTextContent('Tệp đang đặt mật khẩu. Hãy gỡ mật khẩu rồi tải lại.')
    expect(screen.queryByRole('button', { name: /Thử tải lại/ })).not.toBeInTheDocument()
  })

  it('TC-03: tệp trùng báo vị trí bản đã có và gợi ý mở bản đó thay vì lưu thêm', async () => {
    const { user, router } = renderFolder(SEMESTER)
    await user.upload(await screen.findByTestId('upload-input'), [pdf('giao-trinh.pdf')])

    const dialog = await screen.findByRole('dialog', { name: 'Tài liệu này đã có trong kho' })
    expect(dialog).toHaveTextContent('Nội dung của “giao-trinh.pdf” trùng với “Giáo trình chương 1” đang ở Học kỳ một › Giải tích.')
    expect(within(dialog).getByTestId('duplicate-location')).toHaveTextContent('Hãy dùng lại bản đã có')
    expect(within(dialog).getByRole('link', { name: 'Mở thư mục chứa' })).toHaveAttribute(
      'href',
      `${routes.library}?folder=${CALCULUS}`,
    )
    expect(screen.getByTestId('upload-item')).toHaveAttribute('data-state', 'duplicate')
    expect(screen.getByTestId('upload-status')).toHaveTextContent('Đã có trong kho, ở Học kỳ một › Giải tích')

    await user.click(within(dialog).getByRole('link', { name: 'Mở bản đã có' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(documentPath(EXISTING)))
  })

  it('máy chủ tạm không trả lời khi hoàn tất: hiện lỗi kèm nút thử lại, thử lại thì gửi tiếp chứ không bắt đầu lại', async () => {
    let attempts = 0
    server.use(
      http.post('*/api/v1/library/uploads/:id/complete', ({ params }) => {
        attempts += 1
        if (attempts === 1) return apiError(503, 'MALWARE_SCAN_UNAVAILABLE')
        return HttpResponse.json(document(String(params.id), { fileName: 'Bài 4.pdf', sizeBytes: 1200, folderId: CALCULUS }))
      }),
      http.get('*/api/v1/library/uploads/:id', ({ params }) =>
        HttpResponse.json(progress(String(params.id), { fileName: 'Bài 4.pdf', sizeBytes: 1200 }, 1200)),
      ),
    )
    const { user } = renderFolder()
    await user.upload(await screen.findByTestId('upload-input'), [pdf('Bài 4.pdf')])
    await waitFor(() => expect(screen.getByTestId('upload-item')).toHaveAttribute('data-state', 'failed'))
    expect(screen.getByTestId('upload-status')).toHaveTextContent('Chưa kiểm tra được tệp lúc này')

    await user.click(screen.getByRole('button', { name: 'Thử tải lại Bài 4.pdf' }))
    await waitFor(() => expect(screen.getByTestId('upload-item')).toHaveAttribute('data-state', 'done'))
    expect(started).toHaveLength(1)
    expect(parts).toHaveLength(1)
  })

  it('đổi tên tài liệu ngay sau khi tải', async () => {
    let renamed: unknown
    server.use(
      http.patch('*/api/v1/library/documents/:id', async ({ request, params }) => {
        renamed = await request.json()
        return HttpResponse.json({ ...document('up-1', { fileName: 'Bài 1.pdf', sizeBytes: 1200 }), id: String(params.id), title: 'Giới hạn' })
      }),
    )
    const { user } = renderFolder()
    await user.upload(await screen.findByTestId('upload-input'), [pdf('Bài 1.pdf')])
    await user.click(await screen.findByRole('button', { name: 'Đổi tên Bài 1' }))
    const dialog = await screen.findByRole('dialog', { name: 'Đổi tên tài liệu' })
    const field = within(dialog).getByRole('textbox')
    await user.clear(field)
    await user.type(field, 'Giới hạn')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu tên mới' }))
    await waitFor(() => expect(renamed).toEqual({ title: 'Giới hạn' }))
    expect(await screen.findByRole('link', { name: 'Giới hạn' })).toBeInTheDocument()
  })
})
