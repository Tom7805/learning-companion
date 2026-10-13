import { act, fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SIDE_PANEL_DEFAULT_WIDTH, SIDE_PANEL_MIN_WIDTH, useSidePanel } from '@/app/providers/sidePanel'
import { setMedia } from '@/test/media'
import { renderWithProviders } from '@/test/renderWithProviders'
import { SidePanel } from './SidePanel'

function Harness() {
  const panel = useSidePanel()
  return (
    <>
      <button
        type="button"
        onClick={() => panel.open({ id: 'note', title: 'Ghi chú vật lý', render: () => <p>Định luật Ohm: I = U / R</p> })}
      >
        Mở ghi chú
      </button>
      <SidePanel />
    </>
  )
}

async function openPanel() {
  const utils = renderWithProviders(<Harness />)
  await utils.user.click(screen.getByRole('button', { name: 'Mở ghi chú' }))
  return utils
}

const separator = () => screen.getByRole('separator', { name: 'Kéo để đổi độ rộng khung xem bên' })
const maxWidth = () => Math.round(window.innerWidth * 0.6)

describe('SidePanel', () => {
  it('màn hình rộng: mở cạnh vùng làm việc, tiêu điểm vào khung, đóng thì trả tiêu điểm về nút đã mở', async () => {
    const { user } = await openPanel()

    const panel = screen.getByTestId('side-panel')
    expect(panel.tagName).toBe('ASIDE')
    expect(panel).toHaveAccessibleName('Ghi chú vật lý')
    expect(panel).toHaveTextContent('Định luật Ohm: I = U / R')
    expect(panel).toHaveFocus()
    expect(panel).toHaveStyle({ width: `${SIDE_PANEL_DEFAULT_WIDTH}px` })

    await user.click(within(panel).getByRole('button', { name: 'Đóng khung xem bên' }))

    expect(screen.queryByTestId('side-panel')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mở ghi chú' })).toHaveFocus()
  })

  it('đổi độ rộng bằng phím mũi tên trên thanh chia, có giới hạn và được nhớ trên máy', async () => {
    const { user } = await openPanel()
    separator().focus()
    expect(separator()).toHaveAttribute('aria-valuenow', String(SIDE_PANEL_DEFAULT_WIDTH))
    expect(separator()).toHaveAttribute('aria-valuemin', String(SIDE_PANEL_MIN_WIDTH))

    await user.keyboard('{ArrowLeft}')
    expect(separator()).toHaveAttribute('aria-valuenow', String(SIDE_PANEL_DEFAULT_WIDTH + 24))
    await user.keyboard('{Shift>}{ArrowRight}{/Shift}')
    expect(separator()).toHaveAttribute('aria-valuenow', String(SIDE_PANEL_DEFAULT_WIDTH + 24 - 96))
    await user.keyboard('{End}')
    expect(separator()).toHaveAttribute('aria-valuenow', String(SIDE_PANEL_MIN_WIDTH))
    await user.keyboard('{ArrowRight}')
    expect(separator()).toHaveAttribute('aria-valuenow', String(SIDE_PANEL_MIN_WIDTH))
    await user.keyboard('{Home}')
    expect(separator()).toHaveAttribute('aria-valuenow', String(maxWidth()))
    expect(screen.getByTestId('side-panel')).toHaveStyle({ width: `${maxWidth()}px` })

    expect(window.localStorage.getItem('lc:side-panel-width')).toBe(String(maxWidth()))
  })

  it('kéo thanh chia sang trái thì khung rộng ra', async () => {
    await openPanel()

    fireEvent.pointerDown(separator(), { clientX: 600, pointerId: 1 })
    fireEvent.pointerMove(separator(), { clientX: 520, pointerId: 1 })
    fireEvent.pointerUp(separator(), { pointerId: 1 })
    fireEvent.pointerMove(separator(), { clientX: 300, pointerId: 1 })

    expect(separator()).toHaveAttribute('aria-valuenow', String(SIDE_PANEL_DEFAULT_WIDTH + 80))
  })

  it('phóng toàn màn hình rồi Esc thì về lại khung bên, tiêu điểm ở nút phóng to', async () => {
    const { user } = await openPanel()

    await user.click(screen.getByRole('button', { name: 'Phóng toàn màn hình' }))
    const overlay = screen.getByTestId('side-panel')
    expect(overlay.tagName).toBe('DIALOG')
    expect(overlay).toHaveAttribute('data-fullscreen', 'true')
    const shrink = within(overlay).getByRole('button', { name: 'Thu nhỏ về khung bên' })
    expect(shrink).toHaveAttribute('aria-pressed', 'true')

    fireEvent(overlay, new Event('cancel', { cancelable: true }))

    const docked = screen.getByTestId('side-panel')
    expect(docked.tagName).toBe('ASIDE')
    expect(within(docked).getByRole('button', { name: 'Phóng toàn màn hình' })).toHaveFocus()
  })

  it('Esc khi tiêu điểm trong khung bên thì đóng khung', async () => {
    const { user } = await openPanel()

    await user.keyboard('{Escape}')

    expect(screen.queryByTestId('side-panel')).not.toBeInTheDocument()
  })

  it('TC-02: điện thoại mở khung toàn màn hình, không có nút phóng to, Esc đóng hẳn', async () => {
    setMedia({ width: 360 })
    await openPanel()

    const overlay = screen.getByTestId('side-panel')
    expect(overlay.tagName).toBe('DIALOG')
    expect(screen.queryByRole('separator')).not.toBeInTheDocument()
    expect(within(overlay).queryByRole('button', { name: 'Phóng toàn màn hình' })).not.toBeInTheDocument()

    fireEvent(overlay, new Event('cancel', { cancelable: true }))

    expect(screen.queryByTestId('side-panel')).not.toBeInTheDocument()
  })

  it('xoay màn hình từ rộng sang hẹp thì khung đang mở chuyển sang phủ toàn màn hình', async () => {
    await openPanel()
    expect(screen.getByTestId('side-panel').tagName).toBe('ASIDE')

    act(() => setMedia({ width: 800 }))

    expect(screen.getByTestId('side-panel').tagName).toBe('DIALOG')
  })
})
