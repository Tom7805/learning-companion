import { useQueryClient } from '@tanstack/react-query'
import { FilePlus2, Mail } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { documentPath, routes } from '@/app/routes'
import { useAuth } from '@/features/auth'
import { http } from '@/shared/api/httpClient'
import { queryKeys } from '@/shared/api/queryKeys'

const pill =
  'inline-flex items-center gap-2 rounded-full border-[1.5px] border-ink bg-sun px-4 py-2 text-sm font-medium shadow-pop disabled:opacity-60'

/** Lối tắt cho môi trường thử, chỉ hiện khi chạy "npm run dev": hộp thư thử và tạo tài liệu mẫu. */
export function DevToolbar() {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { account } = useAuth()
  const [creating, setCreating] = useState(false)
  if (!import.meta.env.DEV || location.pathname.startsWith('/dev/')) return null

  const createSample = async () => {
    if (!account) return
    setCreating(true)
    try {
      const { data } = await http.post<{ id: string }>('/dev/library/sample', { email: account.email })
      await queryClient.invalidateQueries({ queryKey: queryKeys.documents })
      navigate(documentPath(data.id))
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="fixed bottom-4 right-4 z-40 hidden flex-col items-end gap-2 sm:flex">
      {account && (
        <button type="button" className={pill} onClick={() => void createSample()} disabled={creating}>
          <FilePlus2 className="size-4" aria-hidden="true" />
          {t('dev.sampleDocument')}
        </button>
      )}
      <Link to={routes.devMailbox} target="_blank" className={pill}>
        <Mail className="size-4" aria-hidden="true" />
        {t('dev.mailbox')}
      </Link>
    </div>
  )
}
