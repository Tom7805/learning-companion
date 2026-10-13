import { LayoutGrid } from 'lucide-react'
import { routes } from '@/app/routes'
import type { NavigationEntry } from '@/app/navigation'

/** Mục của trang chính trên thanh bên. */
export const dashboardNavigation: NavigationEntry[] = [
  { id: 'home', to: routes.home, label: 'home.nav', icon: LayoutGrid, end: true, order: 0 },
]
