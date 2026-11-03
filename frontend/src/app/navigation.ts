import type { LucideIcon } from 'lucide-react'
import { adminNavigation } from '@/features/admin/navigation'
import { dashboardNavigation } from '@/features/dashboard/navigation'
import { libraryNavigation } from '@/features/library/navigation'

/** Một mục chức năng trên thanh bên. {@code label} là khóa dịch; {@code order} nhỏ đứng trước. */
export interface NavigationEntry {
  id: string
  to: string
  label: string
  icon: LucideIcon
  /** Chỉ sáng khi đúng đường dẫn này (trang chính), không sáng cho các trang con. */
  end?: boolean
  order: number
}

/**
 * Nhóm mục chức năng của thanh bên (NCL-02-CN-001). Mỗi chức năng tự khai báo mục của mình trong tệp navigation.ts
 * của nó; khi một chức năng mới hoàn thành, chỉ cần thêm danh sách của nó vào đây, không phải sửa thanh bên.
 */
export const navigation: NavigationEntry[] = [...dashboardNavigation, ...libraryNavigation].sort(
  (left, right) => left.order - right.order,
)

/** Mục thanh bên theo vai trò: quản trị viên chỉ thấy khu quản trị, người học thấy các chức năng học tập (QTN-01). */
export function navigationFor(role: 'LEARNER' | 'ADMIN' | undefined): NavigationEntry[] {
  return role === 'ADMIN' ? adminNavigation : navigation
}
