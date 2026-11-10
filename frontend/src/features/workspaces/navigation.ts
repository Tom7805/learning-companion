import { Layers } from 'lucide-react'
import { routes } from '@/app/routes'
import type { NavigationEntry } from '@/app/navigation'

/** Mục không gian học trên thanh bên; danh sách từng không gian nằm ở khu riêng ngay bên dưới. */
export const workspacesNavigation: NavigationEntry[] = [
  { id: 'workspaces', to: routes.workspaces, label: 'workspaces.nav', icon: Layers, order: 5 },
]
