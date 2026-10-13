import { lazy, Suspense } from 'react'
import { Outlet } from 'react-router'

// Thanh công cụ chỉ có khi chạy "npm run dev"; bản dựng thật không chứa mã của nó.
const DevToolbar = import.meta.env.DEV
  ? lazy(async () => ({ default: (await import('@/dev/DevToolbar')).DevToolbar }))
  : () => null

export function Root() {
  return (
    <>
      <Outlet />
      <Suspense fallback={null}>
        <DevToolbar />
      </Suspense>
    </>
  )
}
