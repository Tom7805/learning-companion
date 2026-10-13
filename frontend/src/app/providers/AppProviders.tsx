import type { ReactNode } from 'react'
import { Toaster } from '@/shared/ui'
import { I18nProvider } from './I18nProvider'
import { QueryProvider } from './QueryProvider'
import { ThemeProvider } from './ThemeProvider'

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <I18nProvider>
      <QueryProvider>
        <ThemeProvider>
          {children}
          <Toaster />
        </ThemeProvider>
      </QueryProvider>
    </I18nProvider>
  )
}
