import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { recentStore } from '@/app/store/recentStore'
import { uiStore } from '@/app/store/uiStore'
import '@/shared/i18n'
import { toast } from '@/shared/ui'
import { installMatchMedia, resetMedia } from './media'
import { server } from './mocks/server'

installMatchMedia()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  window.sessionStorage.clear()
  window.localStorage.clear()
  uiStore.reset()
  recentStore.reset()
  toast.clear()
  resetMedia()
  delete document.documentElement.dataset.theme
  delete document.documentElement.dataset.fontSize
})
afterAll(() => server.close())
