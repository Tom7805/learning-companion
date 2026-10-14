import type { TFunction } from 'i18next'
import type { AppNotification } from '../types'

export type NotificationCategory = 'security' | 'data' | 'general'

const CATEGORY: Record<string, NotificationCategory> = {
  NEW_DEVICE_LOGIN: 'security',
  PASSWORD_CHANGED: 'security',
  DATA_EXPORT_READY: 'data',
}

export function notificationCategory(type: string): NotificationCategory {
  return CATEGORY[type] ?? 'general'
}

interface Formatters {
  dateTime: (iso: string) => string
  bytes: (value: number) => string
}

const text = (value: unknown) => (typeof value === 'string' ? value : undefined)
const number = (value: unknown) => (typeof value === 'number' ? value : undefined)

/**
 * Tiêu đề và dòng mô tả của một thông báo, dựng từ loại và tham số theo ngôn ngữ đang dùng. Loại chưa biết (ví dụ
 * do bản giao diện cũ gặp chức năng mới) vẫn hiển thị được dưới dạng chung.
 */
export function describeNotification(notification: AppNotification, t: TFunction, format: Formatters) {
  const { params } = notification
  switch (notification.type) {
    case 'DATA_EXPORT_READY': {
      const files = number(params.fileCount)
      const size = number(params.sizeBytes)
      const expiresAt = text(params.expiresAt)
      const parts = [
        files !== undefined ? t('notifications.types.DATA_EXPORT_READY.files', { count: files }) : undefined,
        size !== undefined ? format.bytes(size) : undefined,
        expiresAt ? t('notifications.types.DATA_EXPORT_READY.until', { time: format.dateTime(expiresAt) }) : undefined,
      ]
      return { title: t('notifications.types.DATA_EXPORT_READY.title'), body: parts.filter(Boolean).join(' · ') }
    }
    case 'NEW_DEVICE_LOGIN': {
      const device = t('notifications.types.NEW_DEVICE_LOGIN.device', {
        browser: text(params.browser) ?? '?',
        os: text(params.operatingSystem) ?? '?',
      })
      const nearby = params.localNetwork === true ? t('devices.location.local') : t('devices.location.unknown')
      const location = text(params.location) ?? nearby
      return { title: t('notifications.types.NEW_DEVICE_LOGIN.title'), body: `${device} · ${location}` }
    }
    case 'PASSWORD_CHANGED': {
      const change = text(params.change) === 'RESET' || text(params.change) === 'SET' ? text(params.change) : 'CHANGED'
      return {
        title: t(`notifications.types.PASSWORD_CHANGED.${change}`),
        body: t('notifications.types.PASSWORD_CHANGED.body'),
      }
    }
    default:
      return { title: t('notifications.types.fallback'), body: '' }
  }
}
