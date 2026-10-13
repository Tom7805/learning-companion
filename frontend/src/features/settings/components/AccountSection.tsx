import { useTranslation } from 'react-i18next'
import { DataExportSection, DeleteAccountSection } from '@/features/privacy'
import { LinkedAccountsSection } from './LinkedAccountsSection'

/**
 * Mục Tài khoản trong cài đặt: cách đăng nhập (tài khoản liên kết, NCL-01-CN-007), quyền mang dữ liệu đi (xuất toàn
 * bộ dữ liệu, NCL-01-CN-008) và quyền xóa dữ liệu (xóa tài khoản có thời gian chờ, NCL-01-CN-009).
 */
export function AccountSection({ onNavigate }: { onNavigate: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-1 pr-10">
        <h2 className="text-2xl tracking-tight">{t('accountSettings.title')}</h2>
        <p className="text-sm text-muted">{t('accountSettings.subtitle')}</p>
      </header>
      <LinkedAccountsSection onNavigate={onNavigate} />
      <DataExportSection onNavigate={onNavigate} />
      <DeleteAccountSection onNavigate={onNavigate} />
    </div>
  )
}
