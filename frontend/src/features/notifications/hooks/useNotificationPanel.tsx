import { useTranslation } from 'react-i18next'
import { useSidePanel } from '@/app/providers/sidePanel'
import { NotificationPanel } from '../components/NotificationPanel'

export const NOTIFICATION_PANEL_ID = 'notifications'

/** Mở, đóng ngăn thông báo trong khung xem bên; dùng chung cho chuông và các trang dẫn tới thông báo. */
export function useNotificationPanel() {
  const { t } = useTranslation()
  const panel = useSidePanel()
  const isOpen = panel.content?.id === NOTIFICATION_PANEL_ID
  const open = () =>
    panel.open({ id: NOTIFICATION_PANEL_ID, title: t('notifications.title'), render: () => <NotificationPanel /> })
  return {
    isOpen,
    open,
    toggle: () => (isOpen ? panel.close() : open()),
  }
}
