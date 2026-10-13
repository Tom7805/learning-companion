import { Minus, Plus } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui'

const VIEWPORT = 280
const OUTPUT = 512
const MAX_ZOOM = 3
const KEY_STEP = 12

interface AvatarCropperProps {
  image: HTMLImageElement | null
  saving?: boolean
  onCancel: () => void
  onConfirm: (cropped: Blob) => void
}

interface Crop {
  zoom: number
  x: number
  y: number
}

/**
 * Cắt ảnh đại diện vuông: kéo để chọn vị trí, thu phóng bằng thanh trượt; làm được cả bằng bàn phím
 * (mũi tên để dịch, + và − để thu phóng). Ảnh cắt xong được xuất JPG 512 điểm ảnh rồi mới gửi lên.
 */
export function AvatarCropper({ image, saving, onCancel, onConfirm }: AvatarCropperProps) {
  const { t } = useTranslation()
  const dialog = useRef<HTMLDialogElement>(null)
  const viewport = useRef<HTMLDivElement>(null)
  const drag = useRef<{ x: number; y: number; crop: Crop } | null>(null)
  const [crop, setCrop] = useState<Crop>({ zoom: 1, x: 0, y: 0 })
  const titleId = useId()
  const hintId = useId()
  const open = image !== null

  useEffect(() => {
    const element = dialog.current
    if (!element) return
    if (open && !element.open) {
      if (typeof element.showModal === 'function') element.showModal()
      else element.setAttribute('open', '')
      viewport.current?.focus()
    } else if (!open && element.open) {
      if (typeof element.close === 'function') element.close()
      else element.removeAttribute('open')
    }
  }, [open])

  const cover = image ? Math.max(VIEWPORT / image.naturalWidth, VIEWPORT / image.naturalHeight) : 1

  /** Giữ ảnh luôn phủ kín khung vuông. */
  const clamp = (next: Crop): Crop => {
    if (!image) return next
    const zoom = Math.min(MAX_ZOOM, Math.max(1, next.zoom))
    const scale = cover * zoom
    const maxX = Math.max(0, (image.naturalWidth * scale - VIEWPORT) / 2)
    const maxY = Math.max(0, (image.naturalHeight * scale - VIEWPORT) / 2)
    return { zoom, x: Math.min(maxX, Math.max(-maxX, next.x)), y: Math.min(maxY, Math.max(-maxY, next.y)) }
  }

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { x: event.clientX, y: event.clientY, crop }
  }
  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const start = drag.current
    if (!start) return
    setCrop(clamp({ ...start.crop, x: start.crop.x + event.clientX - start.x, y: start.crop.y + event.clientY - start.y }))
  }
  const onPointerUp = () => {
    drag.current = null
  }

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [KEY_STEP, 0],
      ArrowRight: [-KEY_STEP, 0],
      ArrowUp: [0, KEY_STEP],
      ArrowDown: [0, -KEY_STEP],
    }
    if (moves[event.key]) {
      event.preventDefault()
      const [dx, dy] = moves[event.key]
      setCrop((current) => clamp({ ...current, x: current.x + dx, y: current.y + dy }))
    } else if (event.key === '+' || event.key === '=') {
      event.preventDefault()
      setCrop((current) => clamp({ ...current, zoom: current.zoom + 0.1 }))
    } else if (event.key === '-') {
      event.preventDefault()
      setCrop((current) => clamp({ ...current, zoom: current.zoom - 0.1 }))
    }
  }

  const confirm = () => {
    if (!image) return
    const scale = cover * crop.zoom
    const left = VIEWPORT / 2 + crop.x - (image.naturalWidth * scale) / 2
    const top = VIEWPORT / 2 + crop.y - (image.naturalHeight * scale) / 2
    const canvas = document.createElement('canvas')
    canvas.width = OUTPUT
    canvas.height = OUTPUT
    const context = canvas.getContext('2d')
    if (!context) return
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, OUTPUT, OUTPUT)
    context.drawImage(image, -left / scale, -top / scale, VIEWPORT / scale, VIEWPORT / scale, 0, 0, OUTPUT, OUTPUT)
    canvas.toBlob((blob) => blob && onConfirm(blob), 'image/jpeg', 0.92)
  }

  const scale = cover * crop.zoom

  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        onCancel()
      }}
      className="m-auto w-[min(94vw,400px)] rounded-card border-[1.5px] border-ink bg-surface p-0 text-ink shadow-pop backdrop:bg-scrim/45"
    >
      {image && (
        <div className="flex flex-col gap-5 p-6">
          <div className="flex flex-col gap-1">
            <h2 id={titleId} className="text-xl font-semibold">
              {t('profile.avatar.cropTitle')}
            </h2>
            <p id={hintId} className="text-sm text-muted">
              {t('profile.avatar.cropHint')}
            </p>
          </div>
          <div
            ref={viewport}
            role="img"
            tabIndex={0}
            aria-label={t('profile.avatar.cropArea')}
            aria-describedby={hintId}
            data-testid="avatar-crop-area"
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={onPointerUp}
            onKeyDown={onKeyDown}
            className="relative mx-auto cursor-grab touch-none overflow-hidden rounded-2xl border-[1.5px] border-ink bg-paper outline-none focus-visible:ring-4 focus-visible:ring-focus/30 active:cursor-grabbing"
            style={{ width: VIEWPORT, height: VIEWPORT }}
          >
            <img
              src={image.src}
              alt=""
              draggable={false}
              className="pointer-events-none absolute left-1/2 top-1/2 max-w-none select-none"
              style={{
                width: image.naturalWidth * scale,
                height: image.naturalHeight * scale,
                transform: `translate(calc(-50% + ${crop.x}px), calc(-50% + ${crop.y}px))`,
              }}
            />
            {/* Vùng ngoài vòng tròn tối đi để thấy ảnh đại diện sẽ trông thế nào. */}
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 rounded-full shadow-[0_0_0_999px_rgba(29,29,31,0.45)]"
            />
          </div>
          <div className="flex items-center gap-3">
            <Minus className="size-4 shrink-0 text-muted" aria-hidden="true" />
            <input
              type="range"
              min={1}
              max={MAX_ZOOM}
              step={0.01}
              value={crop.zoom}
              onChange={(event) => setCrop((current) => clamp({ ...current, zoom: Number(event.target.value) }))}
              aria-label={t('profile.avatar.zoom')}
              className="w-full accent-ink"
            />
            <Plus className="size-4 shrink-0 text-muted" aria-hidden="true" />
          </div>
          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <Button variant="outline" onClick={onCancel} disabled={saving}>
              {t('profile.avatar.cancel')}
            </Button>
            <Button variant="dark" onClick={confirm} loading={saving} data-testid="avatar-crop-save">
              {t('profile.avatar.save')}
            </Button>
          </div>
        </div>
      )}
    </dialog>
  )
}
