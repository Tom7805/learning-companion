import { MessageSquare, Pin, Search, X } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, useLocation, useNavigate } from 'react-router'
import { chatPath, routes, workspacePath } from '@/app/routes'
import { cn } from '@/shared/lib/cn'
import { IconButton } from '@/shared/ui'
import { useConversations } from '../api/queries'
import { groupConversations } from '../lib/history'
import { ConversationMenu } from './ConversationMenu'

/** Đợi người học ngừng gõ bấy lâu rồi mới tìm. */
const SEARCH_DELAY_MS = 250

/**
 * Lịch sử hội thoại riêng trên thanh bên (NCL-07-CN-003): đã ghim lên đầu, còn lại chia theo hôm nay, hôm qua, bảy ngày
 * qua và cũ hơn; ô tìm theo tên và nội dung. Mỗi hội thoại có trình đơn đổi tên, ghim, chuyển vào không gian học, tải
 * về và xóa. Hội thoại của không gian học nằm trong thẻ Hỏi đáp của không gian học đó.
 */
export function SidebarConversations({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation()
  const headingId = useId()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [searching, setSearching] = useState(false)
  const [typed, setTyped] = useState('')
  const [query, setQuery] = useState('')
  const input = useRef<HTMLInputElement>(null)
  const toggle = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(typed.trim()), SEARCH_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [typed])
  useEffect(() => {
    if (searching) input.current?.focus()
  }, [searching])

  const conversations = useConversations(undefined, query)
  const list = conversations.data ?? []
  const groups = groupConversations(list)

  const closeSearch = () => {
    setSearching(false)
    setTyped('')
    setQuery('')
    toggle.current?.focus()
  }

  return (
    <section aria-labelledby={headingId} className="flex min-h-0 flex-col gap-1.5" data-testid="sidebar-conversations">
      <div className="flex items-center justify-between gap-2 pl-3 pr-1">
        <h2 id={headingId} className="text-xs font-semibold uppercase tracking-wide text-on-night/70">
          {t('assistant.history.title')}
        </h2>
        <IconButton
          ref={toggle}
          tone="night"
          label={searching ? t('assistant.history.closeSearch') : t('assistant.history.search')}
          aria-expanded={searching}
          icon={searching ? <X className="size-4" /> : <Search className="size-4" />}
          onClick={() => (searching ? closeSearch() : setSearching(true))}
          className="size-8"
          data-testid="conversation-search-toggle"
        />
      </div>
      {searching && (
        <div className="px-1">
          <input
            ref={input}
            type="search"
            value={typed}
            onChange={(event) => setTyped(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Escape') {
                event.preventDefault()
                closeSearch()
              }
            }}
            maxLength={100}
            aria-label={t('assistant.history.search')}
            placeholder={t('assistant.history.searchPlaceholder')}
            data-testid="conversation-search"
            className="h-10 w-full rounded-lg border border-on-night/25 bg-on-night/10 px-3 text-sm text-on-night outline-none placeholder:text-on-night/60 focus-visible:border-sun focus-visible:ring-2 focus-visible:ring-sun/60"
          />
        </div>
      )}
      {conversations.isError && <p className="px-3 text-[0.8125rem] leading-snug text-on-night/70">{t('assistant.history.loadFailed')}</p>}
      {conversations.isSuccess && list.length === 0 && (
        <p className="px-3 text-[0.8125rem] leading-snug text-on-night/70" role={query ? 'status' : undefined}>
          {query ? t('assistant.history.noResults', { query }) : t('assistant.history.empty')}
        </p>
      )}
      {groups.length > 0 && (
        <div className="flex min-h-0 flex-col gap-2 overflow-y-auto" aria-busy={conversations.isFetching || undefined}>
          {groups.map(({ group, items }) => (
            <div key={group} className="flex flex-col gap-0.5" data-testid="conversation-group" data-group={group}>
              <h3 className="px-3 pt-1 text-[0.75rem] font-medium text-on-night/70">{t(`assistant.history.groups.${group}`)}</h3>
              <ul className="flex flex-col gap-0.5">
                {items.map((conversation) => (
                  <li key={conversation.id} className="flex items-center gap-1 pr-1" data-testid="sidebar-conversation">
                    <NavLink
                      to={chatPath(conversation.id)}
                      onClick={onNavigate}
                      aria-label={conversation.pinned ? `${conversation.title} (${t('assistant.history.pinnedMark')})` : undefined}
                      className={({ isActive }) =>
                        cn(
                          'flex min-w-0 flex-1 items-center gap-2.5 rounded-lg px-3 py-2 text-sm',
                          isActive ? 'bg-on-night/15 text-on-night' : 'text-on-night/80 hover:bg-on-night/10 hover:text-on-night',
                        )
                      }
                    >
                      {conversation.pinned ? (
                        <Pin className="size-4 shrink-0" aria-hidden="true" />
                      ) : (
                        <MessageSquare className="size-4 shrink-0" aria-hidden="true" />
                      )}
                      <span className="truncate" data-testid="sidebar-conversation-title">
                        {conversation.title}
                      </span>
                    </NavLink>
                    <ConversationMenu
                      conversation={conversation}
                      onDeleted={() => {
                        if (pathname === chatPath(conversation.id)) void navigate(routes.chat)
                      }}
                      onMoved={(workspaceId) => {
                        if (pathname === chatPath(conversation.id)) {
                          void navigate(`${workspacePath(workspaceId)}?tab=assistant&conversation=${conversation.id}`)
                        }
                      }}
                    />
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
