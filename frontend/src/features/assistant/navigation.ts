import { MessageSquarePlus } from 'lucide-react'
import { routes } from '@/app/routes'
import type { NavigationEntry } from '@/app/navigation'

/** Hành động chính trên thanh bên: mở hội thoại mới với trợ lý (NCL-07-CN-001). */
export const assistantNavigation: NavigationEntry[] = [
  { id: 'chat', to: routes.chat, label: 'assistant.nav', icon: MessageSquarePlus, order: 1 },
]
