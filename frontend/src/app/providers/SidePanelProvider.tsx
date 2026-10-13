import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { localStore } from '@/shared/lib/storage'
import {
  SIDE_PANEL_DEFAULT_WIDTH,
  SIDE_PANEL_MIN_WIDTH,
  SidePanelContext,
  type SidePanelContent,
} from './sidePanel'

const WIDTH_KEY = 'lc:side-panel-width'

/**
 * Khung xem bên (NCL-02-CN-001): mở một nội dung cạnh vùng làm việc chính, kéo được độ rộng (nhớ trên máy) và phóng
 * toàn màn hình. Mỗi lần chỉ một nội dung; mở nội dung khác thì thay chỗ.
 */
export function SidePanelProvider({ children }: { children: ReactNode }) {
  const [content, setContent] = useState<SidePanelContent | null>(null)
  const [fullscreen, setFullscreen] = useState(false)
  const [width, setStoredWidth] = useState(() => {
    const stored = localStore.get<number>(WIDTH_KEY)
    return typeof stored === 'number' && stored >= SIDE_PANEL_MIN_WIDTH ? stored : SIDE_PANEL_DEFAULT_WIDTH
  })

  const open = useCallback((next: SidePanelContent) => setContent(next), [])
  const close = useCallback(() => {
    setContent(null)
    setFullscreen(false)
  }, [])
  const setWidth = useCallback((next: number) => {
    const rounded = Math.round(Math.max(SIDE_PANEL_MIN_WIDTH, next))
    setStoredWidth(rounded)
    localStore.set(WIDTH_KEY, rounded)
  }, [])

  const value = useMemo(
    () => ({ content, fullscreen, width, open, close, setFullscreen, setWidth }),
    [content, fullscreen, width, open, close, setWidth],
  )
  return <SidePanelContext.Provider value={value}>{children}</SidePanelContext.Provider>
}
