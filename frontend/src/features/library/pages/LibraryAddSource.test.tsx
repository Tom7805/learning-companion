import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { DocumentCard, FolderTree, PasteTextRequest, WebImportRequest } from '../types'
import { LibraryPage } from './LibraryPage'

const DATA_STRUCTURES = '00000000-0000-4000-8000-0000000000b1'
const ARTICLE = 'https://bai-viet.example/thuat-toan-sap-xep'

const tree: FolderTree = {
  folders: [{ id: DATA_STRUCTURES, parentId: null, name: 'Cấu trúc dữ liệu', documentCount: 0, createdAt: '2026-10-01T00:00:00Z' }],
  rootDocumentCount: 0,
  totalDocumentCount: 0,
  starredCount: 0,
}

let webRequests: WebImportRequest[] = []
let textRequests: PasteTextRequest[] = []
let documents: DocumentCard[] = []

function card(partial: Partial<DocumentCard>): DocumentCard {
  return {
    id: `doc-${documents.length + 1}`,
    title: '',
    type: 'WEB',
    originalFilename: 'bai-viet.example.html',
    sizeBytes: 2048,
    createdAt: '2026-11-04T03:00:00Z',
    folderId: DATA_STRUCTURES,
    status: 'PENDING',
    ...partial,
  }
}

beforeEach(() => {
  webRequests = []
  textRequests = []
  documents = []
  server.use(
    http.get('*/api/v1/library/folders', () => HttpResponse.json(tree)),
    http.get('*/api/v1/library/documents', () => HttpResponse.json(documents)),
    http.post('*/api/v1/library/sources/web', async ({ request }) => {
      const body = (await request.json()) as WebImportRequest
      webRequests.push(body)
      if (body.url.includes('192.168.')) return apiError(422, 'WEB_ADDRESS_BLOCKED')
      if (body.url.includes('thanh-vien')) return apiError(422, 'WEB_PAGE_UNAVAILABLE', undefined, { reason: 'LOGIN_REQUIRED' })
      if (body.url.includes('trung-lap')) {
        return apiError(409, 'LIBRARY_DOCUMENT_DUPLICATE', undefined, {
          documentId: 'doc-old',
          title: 'Bài cũ',
          folderId: null,
          folderPath: [],
        })
      }
      const created = card({ title: 'Thuật toán sắp xếp: nổi bọt, chèn và nhanh', sourceUrl: body.url, folderId: body.folderId })
      documents = [created]
      return HttpResponse.json(created, { status: 201 })
    }),
    http.post('*/api/v1/library/sources/text', async ({ request }) => {
      const body = (await request.json()) as PasteTextRequest
      textRequests.push(body)
      const created = card({ title: body.title, type: 'TEXT', originalFilename: `${body.title}.txt`, folderId: body.folderId })
      documents = [created]
      return HttpResponse.json(created, { status: 201 })
    }),
  )
})

function renderFolder() {
  return renderWithProviders(<LibraryPage />, {
    path: routes.library,
    initialEntries: [`${routes.library}?folder=${DATA_STRUCTURES}`],
    extraRoutes: [{ path: routes.libraryDocument, element: <p>Trang tài liệu</p> }],
  })
}

describe('Thêm tài liệu từ trang web và văn bản dán (NCL-05-CN-004)', () => {
  it('TC-01: dán đường dẫn bài viết vào thư mục đang mở: tài liệu mới hiện trong kho', async () => {
    const { user } = renderFolder()
    await user.click(await screen.findByTestId('add-from-web'))
    const dialog = await screen.findByTestId('add-source-dialog')
    expect(within(dialog).getByTestId('add-source-tab-web')).toHaveAttribute('aria-selected', 'true')

    await user.type(within(dialog).getByTestId('web-url'), ARTICLE)
    await user.click(within(dialog).getByTestId('web-submit'))

    await waitFor(() => expect(webRequests).toEqual([{ url: ARTICLE, folderId: DATA_STRUCTURES }]))
    await waitFor(() => expect(screen.queryByTestId('add-source-dialog')).not.toBeInTheDocument())
    expect(await screen.findByText('Thuật toán sắp xếp: nổi bọt, chèn và nhanh')).toBeInTheDocument()
  })

  it('Thiếu giao thức thì tự thêm https://; đường dẫn sai dạng báo ngay, không gửi', async () => {
    const { user } = renderFolder()
    await user.click(await screen.findByTestId('add-from-web'))
    const field = await screen.findByTestId('web-url')
    await user.type(field, 'khong phai duong dan')
    await user.click(screen.getByTestId('web-submit'))
    expect(await screen.findByText(/Đường dẫn chưa hợp lệ/)).toBeInTheDocument()
    expect(webRequests).toHaveLength(0)

    await user.clear(field)
    await user.type(field, 'bai-viet.example/thuat-toan-sap-xep')
    await user.click(screen.getByTestId('web-submit'))
    await waitFor(() => expect(webRequests[0]?.url).toBe(ARTICLE))
  })

  it('TC-03: địa chỉ mạng nội bộ bị từ chối kèm lời giải thích', async () => {
    const { user } = renderFolder()
    await user.click(await screen.findByTestId('add-from-web'))
    await user.type(await screen.findByTestId('web-url'), 'http://192.168.1.1/admin')
    await user.click(screen.getByTestId('web-submit'))
    const problem = await screen.findByTestId('web-problem')
    expect(problem).toHaveAttribute('data-kind', 'blocked')
    expect(problem).toHaveTextContent('mạng nội bộ')
    expect(screen.queryByTestId('paste-instead')).not.toBeInTheDocument()
  })

  it('TC-04: trang yêu cầu đăng nhập: báo không lấy được và chuyển sang dán văn bản', async () => {
    const { user } = renderFolder()
    await user.click(await screen.findByTestId('add-from-web'))
    await user.type(await screen.findByTestId('web-url'), 'https://thanh-vien.example/bai-viet-rieng')
    await user.click(screen.getByTestId('web-submit'))
    expect(await screen.findByTestId('web-problem')).toHaveTextContent('yêu cầu đăng nhập')

    await user.click(screen.getByTestId('paste-instead'))
    expect(screen.getByTestId('add-source-tab-text')).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByTestId('paste-text-form')).toBeInTheDocument()
  })

  it('Trang đã có trong kho: gợi ý mở bản đã có', async () => {
    const { user } = renderFolder()
    await user.click(await screen.findByTestId('add-from-web'))
    await user.type(await screen.findByTestId('web-url'), 'https://trung-lap.example/a')
    await user.click(screen.getByTestId('web-submit'))
    expect(await screen.findByTestId('web-duplicate')).toHaveTextContent('Bài cũ')
  })

  it('TC-02: dán văn bản kèm tiêu đề: tài liệu văn bản được tạo trong thư mục đang mở', async () => {
    const { user } = renderFolder()
    await user.click(await screen.findByTestId('add-from-text'))
    const dialog = await screen.findByTestId('add-source-dialog')
    await user.click(within(dialog).getByTestId('paste-submit'))
    expect(await within(dialog).findByText('Hãy đặt tiêu đề cho tài liệu.')).toBeInTheDocument()
    expect(within(dialog).getByText('Hãy dán văn bản cần thêm.')).toBeInTheDocument()

    await user.type(within(dialog).getByTestId('paste-title'), 'Ghi chép tuần 3')
    await user.click(within(dialog).getByTestId('paste-text'))
    await user.paste('Đoạn một nói về mảng.\n\nĐoạn hai nói về danh sách liên kết.')
    expect(within(dialog).getByTestId('paste-count')).toHaveTextContent('13 chữ')
    await user.click(within(dialog).getByTestId('paste-submit'))

    await waitFor(() =>
      expect(textRequests).toEqual([
        { title: 'Ghi chép tuần 3', text: 'Đoạn một nói về mảng.\n\nĐoạn hai nói về danh sách liên kết.', folderId: DATA_STRUCTURES },
      ]),
    )
    expect(await screen.findByText('Ghi chép tuần 3')).toBeInTheDocument()
  })

  it('Thẻ chuyển được bằng phím mũi tên', async () => {
    const { user } = renderFolder()
    await user.click(await screen.findByTestId('add-from-web'))
    const webTab = await screen.findByTestId('add-source-tab-web')
    webTab.focus()
    await user.keyboard('{ArrowRight}')
    expect(screen.getByTestId('add-source-tab-text')).toHaveFocus()
    expect(screen.getByTestId('add-source-tab-text')).toHaveAttribute('aria-selected', 'true')
  })
})
