import { describe, expect, it } from 'vitest'
import i18n from '@/shared/i18n'
import type { AppNotification } from '../types'
import { describeNotification, notificationCategory } from './describe'

const t = i18n.getFixedT('vi')
const format = { dateTime: () => '19/10/2026, 10:00', bytes: (value: number) => `${value} B` }

const notification = (type: string, params: Record<string, unknown>): AppNotification => ({
  id: 'n-1',
  type,
  params,
  targetType: 'DATA_EXPORT',
  createdAt: '2026-10-12T03:00:00Z',
})

describe('describeNotification', () => {
  it('gói dữ liệu sẵn sàng: số tệp, dung lượng và hạn tải', () => {
    const text = describeNotification(
      notification('DATA_EXPORT_READY', { fileCount: 12, sizeBytes: 48_640, expiresAt: '2026-10-19T03:00:05Z' }),
      t,
      format,
    )

    expect(text).toEqual({
      title: 'Gói dữ liệu của bạn đã sẵn sàng',
      body: '12 tệp · 48640 B · tải được đến 19/10/2026, 10:00',
    })
    expect(notificationCategory('DATA_EXPORT_READY')).toBe('data')
  })

  it('đăng nhập từ thiết bị mới: thiết bị và vị trí, không có vị trí thì nói mạng nội bộ hay không rõ', () => {
    expect(
      describeNotification(
        notification('NEW_DEVICE_LOGIN', { browser: 'Chrome', operatingSystem: 'Windows', location: 'Hà Nội, VN' }),
        t,
        format,
      ).body,
    ).toBe('Chrome trên Windows · Hà Nội, VN')
    expect(
      describeNotification(notification('NEW_DEVICE_LOGIN', { browser: 'Safari', operatingSystem: 'iOS', localNetwork: true }), t, format)
        .body,
    ).toBe('Safari trên iOS · Mạng nội bộ')
    expect(notificationCategory('NEW_DEVICE_LOGIN')).toBe('security')
  })

  it('đổi, đặt lại hay đặt mật khẩu lần đầu có tiêu đề riêng', () => {
    expect(describeNotification(notification('PASSWORD_CHANGED', { change: 'CHANGED' }), t, format).title).toBe(
      'Mật khẩu vừa được đổi',
    )
    expect(describeNotification(notification('PASSWORD_CHANGED', { change: 'RESET' }), t, format).title).toBe(
      'Mật khẩu vừa được đặt lại',
    )
    expect(describeNotification(notification('PASSWORD_CHANGED', { change: 'SET' }), t, format).title).toBe(
      'Đã đặt mật khẩu cho tài khoản',
    )
  })

  it('loại chưa biết hoặc tham số lạ vẫn hiển thị được, không vỡ', () => {
    expect(describeNotification(notification('STUDIO_READY', { title: 'Bộ thẻ' }), t, format)).toEqual({
      title: 'Bạn có một thông báo mới',
      body: '',
    })
    expect(notificationCategory('STUDIO_READY')).toBe('general')
    expect(describeNotification(notification('DATA_EXPORT_READY', { fileCount: 'nhiều' }), t, format).body).toBe('')
  })
})
