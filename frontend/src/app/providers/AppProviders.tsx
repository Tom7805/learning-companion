import type { ReactNode } from 'react'
import { I18nProvider } from './I18nProvider'
import { QueryProvider } from './QueryProvider'

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <I18nProvider>
      <QueryProvider>{children}</QueryProvider>
    </I18nProvider>
  )
}
