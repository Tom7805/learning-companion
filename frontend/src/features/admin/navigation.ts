import { ShieldCheck } from 'lucide-react'
import { routes } from '@/app/routes'
import type { NavigationEntry } from '@/app/navigation'

/** Thanh bên của quản trị viên chỉ có khu quản trị: tài khoản quản trị không mở chức năng học tập (NCL-03-CN-002). */
export const adminNavigation: NavigationEntry[] = [
  { id: 'admin', to: routes.admin, label: 'admin.nav.home', icon: ShieldCheck, order: 0 },
]
