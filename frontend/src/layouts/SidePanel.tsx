import { Maximize2, Minimize2, X } from 'lucide-react'
import { useEffect, useId, useRef, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { SIDE_PANEL_MIN_WIDTH, useSidePanel } from '@/app/providers/sidePanel'
import { useMediaQuery } from '@/shared/hooks/useMediaQuery'
import { cn } from '@/shared/lib/cn'
import { IconButton } from '@/shared/ui'

const STEP = 24
const MAX_SHARE = 0.6

function maxWidth() {
  return Math.max(SIDE_PANEL_MIN_WIDTH, Math.round(window.innerWidth * MAX_SHARE))
}

/**
 * Khung xem bên phải (NCL-02-CN-001). Màn hình rộng: nằm cạnh vùng làm việc chính, kéo thanh chia (hoặc dùng phím
 * mũi tên khi thanh chia có tiêu điểm) để đổi độ rộng, và phóng toàn màn hình khi cần. Điện thoại và máy tính bảng
 * dọc: luôn mở toàn màn hình. Khi phủ toàn màn hình, khung là một hộp thoại: tiêu điểm nằm trong khung, Esc thu nhỏ
 * (màn hình rộng) hoặc đóng (điện thoại).
 */
export function SidePanel() {
  const { t } = useTranslation()
  const panel = useSidePanel()
  const docked = useMediaQuery('(min-width: 1024px)')
  const titleId = useId()
  const panelId = useId()
  const region = useRef<HTMLElement>(null)
  const opener = useRef<HTMLElement | null>(null)
  const contentId = panel.content?.id

  // Mở nội dung mới thì đưa tiêu điểm vào khung; đóng thì trả về chỗ đã mở.
  useEffect(() => {
    if (!contentId) return undefined
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    region.current?.focus()
    return () => {
      if (opener.current?.isConnected) opener.current.focus()
    }
  }, [contentId])

  // Thu nhỏ từ toàn màn hình về khung bên: tiêu điểm trở lại nút phóng to thay vì rơi về đầu trang.
  const wasFullscreen = useRef(panel.fullscreen)
  useEffect(() => {
    if (wasFullscreen.current && !panel.fullscreen) {
      region.current?.querySelector<HTMLElement>('[data-testid="side-panel-fullscreen"]')?.focus()
    }
    wasFullscreen.current = panel.fullscreen
  }, [panel.fullscreen])

  if (!panel.content) return null
  const overlay = panel.fullscreen || !docked
  const width = Math.min(panel.width, maxWidth())

  const header = (
    <header className="flex items-center gap-2 border-b border-line px-4 py-3 sm:px-5">
      <h2 id={titleId} className="min-w-0 flex-1 truncate text-lg font-semibold">
        {panel.content.title}
      </h2>
      {docked && (
        <IconButton
          label={panel.fullscreen ? t('shell.panel.exitFullscreen') : t('shell.panel.fullscreen')}
          aria-pressed={panel.fullscreen}
          data-testid="side-panel-fullscreen"
          icon={panel.fullscreen ? <Minimize2 className="size-5" /> : <Maximize2 className="size-5" />}
          onClick={() => panel.setFullscreen(!panel.fullscreen)}
        />
      )}
      <IconButton label={t('shell.panel.close')} icon={<X className="size-5" />} onClick={panel.close} />
    </header>
  )
  const body = <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5 sm:px-5">{panel.content.render()}</div>

  if (overlay) {
    return (
      <OverlayPanel
        id={panelId}
        labelledBy={titleId}
        onEscape={() => (panel.fullscreen && docked ? panel.setFullscreen(false) : panel.close())}
      >
        {header}
        {body}
      </OverlayPanel>
    )
  }

  const onKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      panel.close()
    }
  }

  const onSeparatorKey = (event: KeyboardEvent<HTMLDivElement>) => {
    const step = event.shiftKey ? STEP * 4 : STEP
    const targets: Record<string, number> = {
      ArrowLeft: width + step,
      ArrowRight: width - step,
      Home: maxWidth(),
      End: SIDE_PANEL_MIN_WIDTH,
    }
    if (event.key in targets) {
      event.preventDefault()
      panel.setWidth(Math.min(maxWidth(), targets[event.key]))
    }
  }

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    event.preventDefault()
    const startX = event.clientX
    const startWidth = width
    const target = event.currentTarget
    target.setPointerCapture?.(event.pointerId)
    const move = (moveEvent: globalThis.PointerEvent) =>
      panel.setWidth(Math.min(maxWidth(), startWidth + (startX - moveEvent.clientX)))
    const up = () => {
      target.removeEventListener('pointermove', move)
      target.removeEventListener('pointerup', up)
      target.removeEventListener('pointercancel', up)
    }
    target.addEventListener('pointermove', move)
    target.addEventListener('pointerup', up)
    target.addEventListener('pointercancel', up)
  }

  return (
    <>
      <div
        role="separator"
        aria-orientation="vertical"
        aria-label={t('shell.panel.resize')}
        aria-controls={panelId}
        aria-valuemin={SIDE_PANEL_MIN_WIDTH}
        aria-valuemax={maxWidth()}
        aria-valuenow={width}
        tabIndex={0}
        data-testid="side-panel-resize"
        onKeyDown={onSeparatorKey}
        onPointerDown={onPointerDown}
        className="group relative z-10 -mx-1 w-3 shrink-0 cursor-col-resize touch-none rounded-full outline-none focus-visible:ring-4 focus-visible:ring-sun/60"
      >
        <span
          aria-hidden="true"
          className="absolute inset-y-1/3 left-1/2 w-1 -translate-x-1/2 rounded-full bg-on-night/30 transition-colors group-hover:bg-sun group-focus-visible:bg-sun"
        />
      </div>
      <aside
        ref={region}
        id={panelId}
        tabIndex={-1}
        aria-labelledby={titleId}
        data-testid="side-panel"
        onKeyDown={onKeyDown}
        style={{ width }}
        className="flex min-w-0 shrink-0 flex-col overflow-hidden rounded-[1.5rem] bg-surface text-ink outline-none"
      >
        {header}
        {body}
      </aside>
    </>
  )
}

/** Khung phủ toàn màn hình bằng thẻ <dialog> gốc để khóa tiêu điểm bên trong. */
function OverlayPanel({ id, labelledBy, onEscape, children }: {
  id: string
  labelledBy: string
  onEscape: () => void
  children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    if (!dialog || dialog.open) return undefined
    if (typeof dialog.showModal === 'function') dialog.showModal()
    else dialog.setAttribute('open', '')
    return () => {
      if (typeof dialog.close === 'function') dialog.close()
    }
  }, [])
  return (
    <dialog
      ref={ref}
      id={id}
      aria-labelledby={labelledBy}
      data-testid="side-panel"
      data-fullscreen="true"
      onCancel={(event) => {
        event.preventDefault()
        onEscape()
      }}
      className={cn(
        'm-0 h-dvh max-h-none w-screen max-w-none bg-surface p-0 text-ink backdrop:bg-scrim/50',
        'md:m-2 md:h-[calc(100dvh-1rem)] md:w-[calc(100vw-1rem)] md:rounded-[1.5rem] md:border-[1.5px] md:border-ink md:shadow-pop',
      )}
    >
      <div className="flex h-full flex-col">{children}</div>
    </dialog>
  )
}
