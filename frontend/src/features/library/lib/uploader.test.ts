import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import type { DocumentCard, UploadProgress } from '../types'
import { duplicateInfo, fingerprint, runUpload, type UploadPatch, type UploadTransport } from './uploader'

const CHUNK = 4

function networkError() {
  return new AxiosError('Network Error', 'ERR_NETWORK')
}

function serverError(status: number, data: unknown) {
  const headers = new AxiosHeaders()
  return new AxiosError('fail', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: '',
    headers,
    config: { headers },
    data,
  })
}

/** Máy chủ giả: nhận phần theo thứ tự như máy chủ thật, ghi lại các phần đã gửi. */
function fakeServer(size: number) {
  let received = 0
  const sent: number[] = []
  const progress = (): UploadProgress => ({
    id: 'u1',
    fileName: 'a.pdf',
    sizeBytes: size,
    chunkSize: CHUNK,
    partCount: Math.ceil(size / CHUNK),
    receivedBytes: received,
    nextPart: received >= size ? Math.ceil(size / CHUNK) : Math.floor(received / CHUNK),
    status: 'UPLOADING',
    expiresAt: '2026-11-05T00:00:00Z',
  })
  const document = { id: 'd1', title: 'a', type: 'PDF', status: 'PENDING' } as DocumentCard
  const transport: UploadTransport = {
    startUpload: vi.fn(async () => progress()),
    uploadProgress: vi.fn(async () => progress()),
    uploadPart: vi.fn(async (_id: string, index: number, part: Blob) => {
      sent.push(index)
      if (index * CHUNK === received) received += part.size
      return progress()
    }),
    completeUpload: vi.fn(async () => document),
  }
  return { transport, sent, document, setReceived: (bytes: number) => (received = bytes) }
}

function file(size: number) {
  return new File([new Uint8Array(size).fill(65)], 'a.pdf', { type: 'application/pdf' })
}

describe('runUpload', () => {
  it('gửi tệp thành từng phần theo thứ tự, báo tiến độ rồi hoàn tất (TC-01)', async () => {
    const server = fakeServer(10)
    const reports: UploadPatch[] = []
    const result = await runUpload({
      file: file(10),
      folderId: 'giai-tich',
      transport: server.transport,
      signal: new AbortController().signal,
      report: (patch) => reports.push(patch),
    })

    expect(result).toBe(server.document)
    expect(server.sent).toEqual([0, 1, 2])
    expect(server.transport.startUpload).toHaveBeenCalledWith(
      expect.objectContaining({ fileName: 'a.pdf', sizeBytes: 10, folderId: 'giai-tich' }),
      expect.anything(),
    )
    expect(reports.map((patch) => patch.state).filter(Boolean)).toEqual(['preparing', 'uploading', 'verifying'])
    expect(reports.filter((patch) => patch.sentBytes !== undefined).map((patch) => patch.sentBytes)).toContain(8)
  })

  it('mạng mất giữa chừng: chờ mạng có lại, hỏi tiến độ và gửi tiếp từ phần còn thiếu (TC-04)', async () => {
    const server = fakeServer(16)
    let dropped = false
    const realPart = server.transport.uploadPart
    server.transport.uploadPart = vi.fn(async (id: string, index: number, part: Blob, options) => {
      if (index === 2 && !dropped) {
        dropped = true
        throw networkError()
      }
      return realPart(id, index, part, options)
    })
    const waits: number[] = []
    const states: string[] = []
    await runUpload({
      file: file(16),
      folderId: null,
      transport: server.transport,
      signal: new AbortController().signal,
      report: (patch) => patch.state && states.push(patch.state),
      waitForNetwork: async (attempt) => {
        waits.push(attempt)
      },
    })

    expect(waits).toEqual([0])
    expect(states).toContain('offline')
    // Phần 0 và 1 không bị gửi lại; chỉ gửi tiếp từ phần 2.
    expect(server.sent).toEqual([0, 1, 2, 3])
    expect(server.transport.uploadProgress).toHaveBeenCalledTimes(1)
    expect(server.transport.completeUpload).toHaveBeenCalledTimes(1)
  })

  it('máy chủ đã nhận nhiều hơn giao diện tưởng: theo số phần máy chủ báo, không gửi lại từ đầu', async () => {
    const server = fakeServer(12)
    server.transport.uploadPart = vi
      .fn()
      .mockRejectedValueOnce(serverError(409, { code: 'UPLOAD_PART_OUT_OF_ORDER', details: { nextPart: 2, receivedBytes: 8 } }))
      .mockImplementation(async () => {
        server.setReceived(12)
        return { ...(await server.transport.uploadProgress('u1')), nextPart: 3, receivedBytes: 12 }
      })
    await runUpload({
      file: file(12),
      folderId: null,
      transport: server.transport,
      signal: new AbortController().signal,
      report: () => undefined,
    })
    expect((server.transport.uploadPart as ReturnType<typeof vi.fn>).mock.calls.map((call) => call[1])).toEqual([0, 2])
  })

  it('thử lại lần tải đã bắt đầu: không bắt đầu lại mà gửi tiếp từ tiến độ trên máy chủ', async () => {
    const server = fakeServer(12)
    server.setReceived(8)
    await runUpload({
      file: file(12),
      folderId: null,
      uploadId: 'u1',
      transport: server.transport,
      signal: new AbortController().signal,
      report: () => undefined,
    })
    expect(server.transport.startUpload).not.toHaveBeenCalled()
    expect(server.sent).toEqual([2])
  })

  it('bị từ chối (mã độc) thì ném lỗi để hàng đợi hiện lý do, không thử lại (TC-02)', async () => {
    const server = fakeServer(4)
    server.transport.completeUpload = vi.fn().mockRejectedValue(serverError(422, { code: 'UPLOAD_REJECTED', details: { reason: 'MALWARE' } }))
    await expect(
      runUpload({
        file: file(4),
        folderId: null,
        transport: server.transport,
        signal: new AbortController().signal,
        report: () => undefined,
        waitForNetwork: () => Promise.reject(new Error('không được chờ mạng')),
      }),
    ).rejects.toBeInstanceOf(AxiosError)
    expect(server.transport.completeUpload).toHaveBeenCalledTimes(1)
  })
})

describe('fingerprint', () => {
  it('tính SHA-256 dạng hex của tệp', async () => {
    const hash = await fingerprint(new Blob(['abc']))
    expect(hash).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad')
  })
})

describe('duplicateInfo', () => {
  it('đọc mã, tên và đường dẫn thư mục của bản đã có (TC-03)', () => {
    expect(
      duplicateInfo({
        code: 'LIBRARY_DOCUMENT_DUPLICATE',
        message: '',
        status: 409,
        fieldErrors: [],
        details: { documentId: 'd1', title: 'Giáo trình', folderId: 'f1', folderPath: ['Học kỳ một', 'Giải tích'] },
      }),
    ).toEqual({ documentId: 'd1', title: 'Giáo trình', folderId: 'f1', folderPath: ['Học kỳ một', 'Giải tích'] })
    expect(duplicateInfo({ code: 'NOT_FOUND', message: '', status: 404, fieldErrors: [] })).toBeUndefined()
  })
})
