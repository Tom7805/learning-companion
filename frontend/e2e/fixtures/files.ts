/** Tệp mẫu cho kiểm thử tải tài liệu lên (NCL-05-CN-002), dựng ngay trong bộ nhớ. */

export interface UploadFile {
  name: string
  mimeType: string
  buffer: Buffer
}

/** Tệp thử mã độc chuẩn EICAR: vô hại nhưng mọi bộ quét đều coi là mã độc. */
export const EICAR = 'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*'

/**
 * Tài liệu dạng trang hợp lệ một trang, có bảng tham chiếu chéo đúng vị trí. {@code marker} khác nhau cho nội dung khác
 * nhau; {@code paddingBytes} làm tệp lớn lên (ví dụ để tải qua nhiều phần).
 */
export function pdf(name: string, marker: string, paddingBytes = 0): UploadFile {
  const text = marker.normalize('NFD').replace(/[^\x20-\x7e]/g, '').replace(/[()\\]/g, '')
  const padding = paddingBytes > 0 ? `%${'0123456789abcdef'.repeat(Math.ceil(paddingBytes / 16)).slice(0, paddingBytes)}\n` : ''
  const content = `BT /F1 18 Tf 72 720 Td (${text}) Tj ET\n${padding}`
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R >>',
    `<< /Length ${Buffer.byteLength(content, 'latin1')} >>\nstream\n${content}endstream`,
  ]
  let body = '%PDF-1.4\n'
  const offsets: number[] = []
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(body, 'latin1'))
    body += `${index + 1} 0 obj\n${object}\nendobj\n`
  })
  const xref = Buffer.byteLength(body, 'latin1')
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
  body += offsets.map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(body, 'latin1') }
}

export function eicar(name: string): UploadFile {
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(EICAR, 'latin1') }
}
