import { useCallback, useState } from 'react'

export type AutosaveStatus = 'idle' | 'saving' | 'saved' | 'error'

/**
 * Trạng thái lưu tự động cho một nhóm trường: đang lưu khi còn ít nhất một yêu cầu chưa xong,
 * lỗi khi yêu cầu gần nhất thất bại. Mỗi thay đổi lưu ngay, không có nút "Lưu".
 */
export function useAutosave() {
  const [pending, setPending] = useState(0)
  const [failed, setFailed] = useState(false)
  const [touched, setTouched] = useState(false)

  const track = useCallback(async <T,>(save: () => Promise<T>) => {
    setTouched(true)
    setPending((count) => count + 1)
    try {
      const result = await save()
      setFailed(false)
      return result
    } catch (error) {
      setFailed(true)
      throw error
    } finally {
      setPending((count) => count - 1)
    }
  }, [])

  const status: AutosaveStatus = pending > 0 ? 'saving' : failed ? 'error' : touched ? 'saved' : 'idle'
  return { status, track }
}
