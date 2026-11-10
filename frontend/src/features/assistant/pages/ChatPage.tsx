import { MessagesSquare, SearchX } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { chatPath, routes, workspacePath } from '@/app/routes'
import { useAuth } from '@/features/auth'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Button, Skeleton, buttonClasses } from '@/shared/ui'
import { useConversation } from '../api/queries'
import { ChatPanel } from '../components/ChatPanel'

/**
 * Hội thoại riêng với trợ lý (NCL-07-CN-001): {@code /chat} mở hội thoại mới với lời chào và ô nhập lớn, đính kèm
 * tài liệu từ kho hay tải tệp mới; câu hỏi đầu tạo hội thoại và địa chỉ chuyển sang {@code /chat/:id} mà không mất câu
 * trả lời đang hiện. Hội thoại thuộc không gian học được mở trong không gian học đó.
 */
export function ChatPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const { account } = useAuth()
  // Hội thoại vừa tạo ngay trên trang này: giữ nguyên vùng hỏi đáp khi địa chỉ đổi theo. {@code moving}: đã có mã
  // hội thoại mới, đang chờ địa chỉ trang đổi theo.
  const [session, setSession] = useState(() => ({ key: id ?? 'new', id, created: false, moving: false, round: 0 }))
  if (session.moving && id === session.id) {
    setSession({ ...session, moving: false })
  } else if (!session.moving && id !== session.id) {
    setSession({ key: id ?? `new-${session.round + 1}`, id, created: false, moving: false, round: session.round + 1 })
  }
  useEffect(() => {
    if (session.moving && session.id) void navigate(chatPath(session.id), { replace: true })
  }, [navigate, session.id, session.moving])
  const loaded = useConversation(session.created ? undefined : session.id)
  const notFound = loaded.isError && toApiError(loaded.error).status === 404
  const heading = usePageHeading(
    loaded.data?.title ?? (notFound ? t('assistant.notFound.title') : t('assistant.newChat')),
    session.key,
  )

  if (loaded.data?.workspaceId) {
    return <Navigate to={`${workspacePath(loaded.data.workspaceId)}?tab=assistant&conversation=${loaded.data.id}`} replace />
  }

  if (notFound) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-10 text-center" data-testid="conversation-not-found">
        <span aria-hidden="true" className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun">
          <SearchX className="size-7" />
        </span>
        <h1 ref={heading} tabIndex={-1} className="text-2xl tracking-tight outline-none sm:text-3xl">
          {t('assistant.notFound.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('assistant.notFound.body')}</p>
        <Link to={routes.chat} className={buttonClasses('dark', 'lg')}>
          {t('assistant.newChat')}
        </Link>
      </div>
    )
  }

  if (loaded.isError) {
    return (
      <Alert tone="danger">
        <p>{t('assistant.loadFailed')}</p>
        <Button variant="outline" className="mt-3" onClick={() => void loaded.refetch()}>
          {t('common.retry')}
        </Button>
      </Alert>
    )
  }

  if (session.id && !session.created && loaded.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40" />
      </div>
    )
  }

  const name = account?.displayName?.split(' ').at(-1)

  return (
    <div className="mx-auto flex min-h-[calc(100dvh-7rem)] w-full max-w-3xl flex-col gap-4">
      <h1 ref={heading} tabIndex={-1} className={loaded.data ? 'text-2xl tracking-tight outline-none [overflow-wrap:anywhere]' : 'sr-only'}>
        {loaded.data?.title ?? t('assistant.newChat')}
      </h1>
      <ChatPanel
        key={session.key}
        conversation={loaded.data}
        greeting={
          <div className="flex flex-1 flex-col items-center justify-center gap-4 py-10 text-center" data-testid="chat-greeting">
            <span aria-hidden="true" className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun">
              <MessagesSquare className="size-7" />
            </span>
            <p className="text-3xl font-medium tracking-tight">
              {name ? t('assistant.greeting.named', { name }) : t('assistant.greeting.plain')}
            </p>
            <p className="max-w-lg leading-relaxed text-muted">{t('assistant.greeting.body')}</p>
          </div>
        }
        onConversation={(created) => setSession((current) => ({ ...current, id: created, created: true, moving: true }))}
      />
    </div>
  )
}
