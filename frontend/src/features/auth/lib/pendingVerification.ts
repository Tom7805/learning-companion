import { sessionStore } from '@/shared/lib/storage'
import type { PendingVerification } from '../types'

const KEY = 'lc.pendingVerification'

/** Giữ địa chỉ vừa đăng ký và mốc được gửi lại thư, để tải lại trang không mất màn hình kiểm tra hộp thư. */
export const pendingVerification = {
  load: () => sessionStore.get<PendingVerification>(KEY),
  save: (value: PendingVerification) => sessionStore.set(KEY, value),
  clear: () => sessionStore.remove(KEY),
}
