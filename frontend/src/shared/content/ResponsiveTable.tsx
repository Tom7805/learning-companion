import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Bảng trong nội dung có định dạng (NCL-02-CN-003 TC-02): bảng rộng (ví dụ tám cột trên điện thoại) cuộn ngang bên
 * trong khung của nó, trang không bị cuộn ngang. Khung là một vùng có tên và nhận tiêu điểm để cuộn bằng bàn phím.
 */
export function ResponsiveTable({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  return (
    <div
      role="region"
      aria-label={t('content.table.label')}
      tabIndex={0}
      className="my-4 max-w-full overflow-x-auto rounded-lg border border-line"
      data-testid="content-table"
    >
      <table className="rich-table w-max min-w-full border-collapse text-left text-sm">{children}</table>
    </div>
  )
}
