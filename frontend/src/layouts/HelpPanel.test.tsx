import { act, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { initPwa } from '@/app/pwa'
import { routes } from '@/app/routes'
import { renderWithProviders } from '@/test/renderWithProviders'
import { HelpPanel } from './HelpPanel'

function renderHelp() {
  const onOpenAppearance = vi.fn()
  const utils = renderWithProviders(<HelpPanel onOpenAppearance={onOpenAppearance} />)
  return { ...utils, onOpenAppearance }
}

describe('HelpPanel', () => {
  let dispose: (() => void) | undefined
  afterEach(() => dispose?.())

  it('hướng dẫn dùng khung ứng dụng, mở cài đặt giao diện và có liên kết pháp lý', async () => {
    const { user, onOpenAppearance } = renderHelp()

    const help = screen.getByTestId('help-panel')
    for (const title of ['Mở nhanh', 'Thu gọn thanh bên', 'Khung xem bên', 'Dùng bằng bàn phím']) {
      expect(within(help).getByText(title)).toBeInTheDocument()
    }
    await user.click(screen.getByRole('button', { name: 'Mở cài đặt giao diện' }))
    expect(onOpenAppearance).toHaveBeenCalledOnce()
    expect(screen.getByRole('link', { name: 'Điều khoản sử dụng' })).toHaveAttribute('href', routes.terms)
  })

  it('TC-04: trình duyệt mời cài thì cài được ngay từ trợ giúp', async () => {
    dispose = initPwa()
    const prompt = vi.fn(async () => undefined)
    act(() => {
      window.dispatchEvent(
        Object.assign(new Event('beforeinstallprompt', { cancelable: true }), {
          prompt,
          userChoice: Promise.resolve({ outcome: 'accepted' }),
        }),
      )
    })
    const { user } = renderHelp()

    const install = within(screen.getByTestId('help-install'))
    await user.click(install.getByRole('button', { name: 'Cài ứng dụng' }))

    expect(prompt).toHaveBeenCalledOnce()
    expect(await install.findByText('Ứng dụng đã được cài trên máy này.')).toBeInTheDocument()
    expect(await screen.findByText('Đã cài Learning Companion lên máy của bạn.')).toBeInTheDocument()
  })
})
