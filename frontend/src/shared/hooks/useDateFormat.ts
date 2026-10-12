import { useTranslation } from 'react-i18next'
import { formatDateTime, formatRelative } from '@/shared/lib/date'
import { useDisplayTimeZone } from '@/shared/lib/timezone'

/** Định dạng mốc thời gian theo ngôn ngữ giao diện và múi giờ trong hồ sơ người học. */
export function useDateFormat() {
  const { i18n } = useTranslation()
  const timeZone = useDisplayTimeZone()
  return {
    timeZone,
    dateTime: (iso: string) => formatDateTime(iso, i18n.language, timeZone),
    relative: (iso: string) => formatRelative(iso, i18n.language),
  }
}
