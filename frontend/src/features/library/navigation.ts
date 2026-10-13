import { BookOpen } from 'lucide-react'
import { routes } from '@/app/routes'
import type { NavigationEntry } from '@/app/navigation'

/** Mục của thư viện tài liệu trên thanh bên. */
export const libraryNavigation: NavigationEntry[] = [
  { id: 'library', to: routes.library, label: 'library.nav', icon: BookOpen, order: 10 },
]
