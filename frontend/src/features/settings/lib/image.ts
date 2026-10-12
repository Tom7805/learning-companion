export const AVATAR_MAX_BYTES = 5 * 1024 * 1024
export const AVATAR_ACCEPT = 'image/jpeg,image/png,image/webp'

export class ImageFileError extends Error {
  constructor(readonly reason: 'format' | 'size') {
    super(reason)
  }
}

/**
 * Mở tệp người học chọn làm ảnh: kiểm dung lượng rồi giải mã thật bằng trình duyệt,
 * nên tệp văn bản đổi đuôi thành .png cũng bị phát hiện (TC-03).
 */
export function loadImageFile(file: File): Promise<HTMLImageElement> {
  if (file.size > AVATAR_MAX_BYTES) return Promise.reject(new ImageFileError('size'))
  if (file.type && !AVATAR_ACCEPT.split(',').includes(file.type)) return Promise.reject(new ImageFileError('format'))
  const url = URL.createObjectURL(file)
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => {
      if (image.naturalWidth === 0 || image.naturalHeight === 0) reject(new ImageFileError('format'))
      else resolve(image)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new ImageFileError('format'))
    }
    image.src = url
  })
}
