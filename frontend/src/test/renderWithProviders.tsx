import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { I18nextProvider } from 'react-i18next'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import i18n from '@/shared/i18n'

interface Options {
  path?: string
  initialEntries?: (string | { pathname: string; search?: string; state?: unknown })[]
  extraRoutes?: RouteObject[]
}

/** Dựng trang trong bộ định tuyến bộ nhớ cùng các provider thật, kèm userEvent đã cấu hình. */
export function renderWithProviders(ui: ReactElement, { path = '/', initialEntries = [path], extraRoutes = [] }: Options = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const router = createMemoryRouter([{ path, element: ui }, ...extraRoutes], { initialEntries })
  const user = userEvent.setup()
  const utils = render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </I18nextProvider>,
  )
  return { ...utils, user, router, queryClient }
}
