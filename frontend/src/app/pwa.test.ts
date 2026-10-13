import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reloadPage } from '@/shared/lib/navigation'
import { setMedia } from '@/test/media'
import { initPwa, installPrompt, useInstallState } from './pwa'

vi.mock('@/shared/lib/navigation', () => ({ reloadPage: vi.fn() }))

/** Lời mời cài mà Chrome, Edge phát ra khi ứng dụng đủ điều kiện cài. */
function offerInstall(outcome: 'accepted' | 'dismissed') {
  const event = Object.assign(new Event('beforeinstallprompt', { cancelable: true }), {
    prompt: vi.fn(async () => undefined),
    userChoice: Promise.resolve({ outcome }),
  })
  act(() => {
    window.dispatchEvent(event)
  })
  return event
}

function preloadError() {
  const event = new Event('vite:preloadError', { cancelable: true })
  window.dispatchEvent(event)
  return event
}

describe('pwa', () => {
  let dispose: () => void
  beforeEach(() => {
    vi.mocked(reloadPage).mockClear()
  })
  afterEach(() => dispose())

  it('TC-04: giữ lời mời cài của trình duyệt, cài xong thì báo đã cài', async () => {
    dispose = initPwa()
    const { result } = renderHook(() => useInstallState())
    expect(result.current).toBe('unavailable')

    const event = offerInstall('accepted')
    // Không để trình duyệt tự hiện thanh mời cài; ứng dụng hiện nút "Cài ứng dụng" của mình.
    expect(event.defaultPrevented).toBe(true)
    expect(result.current).toBe('available')

    let outcome: string | undefined
    await act(async () => {
      outcome = await installPrompt.install()
    })

    expect(event.prompt).toHaveBeenCalledOnce()
    expect(outcome).toBe('accepted')
    expect(result.current).toBe('installed')
  })

  it('người học từ chối thì không hỏi lại bằng lời mời cũ', async () => {
    dispose = initPwa()
    const { result } = renderHook(() => useInstallState())
    offerInstall('dismissed')

    await act(async () => {
      await installPrompt.install()
    })

    expect(result.current).toBe('unavailable')
    expect(await installPrompt.install()).toBe('dismissed')
  })

  it('cài từ trình đơn của trình duyệt, hoặc đang chạy như ứng dụng đã cài, thì báo đã cài', () => {
    dispose = initPwa()
    const { result } = renderHook(() => useInstallState())
    act(() => {
      window.dispatchEvent(new Event('appinstalled'))
    })
    expect(result.current).toBe('installed')
    dispose()

    setMedia({ standalone: true })
    dispose = initPwa()
    expect(installPrompt.state()).toBe('installed')
  })

  it('gói mã của trang không còn sau khi có bản mới thì tự tải lại, nhưng chỉ một lần', () => {
    dispose = initPwa()

    expect(preloadError().defaultPrevented).toBe(true)
    expect(preloadError().defaultPrevented).toBe(false)

    expect(reloadPage).toHaveBeenCalledOnce()
  })

  it('mất mạng thì không tự tải lại, để trang báo lỗi kèm nút thử lại', () => {
    vi.spyOn(Navigator.prototype, 'onLine', 'get').mockReturnValue(false)
    dispose = initPwa()

    expect(preloadError().defaultPrevented).toBe(false)
    expect(reloadPage).not.toHaveBeenCalled()
  })
})
