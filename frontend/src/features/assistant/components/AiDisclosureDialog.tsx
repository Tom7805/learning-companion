import { Sparkles } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button, Dialog } from '@/shared/ui'
import { useAcknowledgeDisclosure, useAiDisclosure } from '../api/queries'

/**
 * Thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo (NCL-04-CN-005 TC-01, QTN-23): lần đầu mở trợ lý, hộp thoại
 * hiện trước câu hỏi đầu tiên, nêu các giới hạn của trợ lý. Bấm "Đã hiểu" (hay đóng) thì ghi nhận ở máy chủ nên đổi
 * máy cũng không hiện lại; chỉ hiện lại khi nội dung thông báo đổi phiên bản.
 */
export function AiDisclosureDialog() {
  const { t } = useTranslation()
  const disclosure = useAiDisclosure()
  const acknowledge = useAcknowledgeDisclosure()
  const open = disclosure.data !== undefined && !disclosure.data.acknowledged
  const limits = t('ai.disclosure.limits', { returnObjects: true }) as string[]
  const done = () => {
    if (!acknowledge.isPending) acknowledge.mutate()
  }

  return (
    <Dialog
      open={open}
      onClose={done}
      title={t('ai.disclosure.title')}
      description={t('ai.disclosure.intro')}
      footer={
        <Button variant="accent" onClick={done} autoFocus data-testid="ai-disclosure-accept">
          {t('ai.disclosure.acknowledge')}
        </Button>
      }
    >
      <ul className="flex flex-col gap-2.5 text-[0.9375rem] leading-relaxed" data-testid="ai-disclosure">
        {limits.map((limit) => (
          <li key={limit} className="flex items-start gap-2.5">
            <Sparkles className="mt-1 size-4 shrink-0 text-ink-soft" aria-hidden="true" />
            <span>{limit}</span>
          </li>
        ))}
      </ul>
    </Dialog>
  )
}
