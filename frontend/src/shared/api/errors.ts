import { isAxiosError } from 'axios'

export interface FieldError {
  field: string
  code: string
  message: string
}

export interface ApiError {
  code: string
  message: string
  status: number
  fieldErrors: FieldError[]
  requestId?: string
}

/** Chuẩn hóa mọi lỗi (máy chủ, mạng, ngoài dự kiến) về một dạng để giao diện xử lý thống nhất. */
export function toApiError(error: unknown): ApiError {
  if (isAxiosError(error)) {
    const data = error.response?.data as Partial<ApiError> | undefined
    if (error.response && data?.code) {
      return {
        code: data.code,
        message: data.message ?? '',
        status: error.response.status,
        fieldErrors: data.fieldErrors ?? [],
        requestId: data.requestId,
      }
    }
    if (!error.response) {
      return { code: 'NETWORK_ERROR', message: error.message, status: 0, fieldErrors: [] }
    }
    return { code: 'INTERNAL_ERROR', message: error.message, status: error.response.status, fieldErrors: [] }
  }
  return { code: 'INTERNAL_ERROR', message: String(error), status: 0, fieldErrors: [] }
}
