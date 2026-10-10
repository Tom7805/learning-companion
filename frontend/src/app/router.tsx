import { createBrowserRouter, Outlet } from 'react-router'
import { DevMailboxPage } from '@/dev/DevMailboxPage'
import { DevToolbar } from '@/dev/DevToolbar'
import { CheckEmailPage, LoginPage, RegisterPage, VerifyEmailPage } from '@/features/auth'
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage'
import { LegalDocumentPage } from '@/features/privacy/pages/LegalDocumentPage'
import { AppShell } from '@/layouts/AppShell'
import { AuthLayout } from '@/layouts/AuthLayout'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ProtectedRoute } from './ProtectedRoute'
import { routes } from './routes'

function Root() {
  return (
    <>
      <Outlet />
      <DevToolbar />
    </>
  )
}

export const router = createBrowserRouter([
  {
    element: <Root />,
    children: [
      {
        element: <ProtectedRoute />,
        children: [{ element: <AppShell />, children: [{ path: routes.home, element: <DashboardPage /> }] }],
      },
      {
        element: <AuthLayout />,
        children: [
          { path: routes.register, element: <RegisterPage /> },
          { path: routes.checkEmail, element: <CheckEmailPage /> },
          { path: routes.verifyEmail, element: <VerifyEmailPage /> },
          { path: routes.login, element: <LoginPage /> },
          { path: routes.terms, element: <LegalDocumentPage kind="terms" /> },
          { path: routes.privacy, element: <LegalDocumentPage kind="privacy" /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
      ...(import.meta.env.DEV ? [{ path: routes.devMailbox, element: <DevMailboxPage /> }] : []),
    ],
  },
])
