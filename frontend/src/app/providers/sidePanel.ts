import { createContext, useContext, type ReactNode } from 'react'

/** Nội dung mở trong khung xem bên. {@code id} trùng nhau thì mở lại chỉ đưa khung về nội dung đó. */
export interface SidePanelContent {
  id: string
  title: string
  render: () => ReactNode
}

export interface SidePanelValue {
  content: SidePanelContent | null
  fullscreen: boolean
  width: number
  open: (content: SidePanelContent) => void
  close: () => void
  setFullscreen: (fullscreen: boolean) => void
  setWidth: (width: number) => void
}

export const SIDE_PANEL_MIN_WIDTH = 320
export const SIDE_PANEL_DEFAULT_WIDTH = 420

export const SidePanelContext = createContext<SidePanelValue | null>(null)

export function useSidePanel() {
  const value = useContext(SidePanelContext)
  if (!value) throw new Error('useSidePanel phải nằm trong SidePanelProvider')
  return value
}
