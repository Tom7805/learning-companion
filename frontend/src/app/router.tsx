import { createBrowserRouter, Outlet } from 'react-router'
import { DevMailboxPage } from '@/dev/DevMailboxPage'
import { DevOAuthAuthorizePage } from '@/dev/DevOAuthAuthorizePage'
import { DevToolbar } from '@/dev/DevToolbar'
import {
  CheckEmailPage,
  DevicesPage,
  ForgotPasswordPage,
  LoginPage,
  OAuthCallbackPage,
  RegisterPage,
  ResetPasswordPage,
  RevokeDevicePage,
  SecurityPage,
  VerifyEmailPage,
} from '@/features/auth'
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage'
import { DocumentPage, LibraryPage } from '@/features/library'
import {
  DataExportDownloadPage,
  DeleteAccountFlow,
  DeletionScheduledPage,
  LegalDocumentPage,
  PrivacyPage,
} from '@/features/privacy'
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
        children: [
          {
            element: <AppShell />,
            children: [
              { path: routes.home, element: <DashboardPage /> },
              { path: routes.library, element: <LibraryPage /> },
              { path: routes.libraryDocument, element: <DocumentPage /> },
              { path: routes.devices, element: <DevicesPage /> },
              { path: routes.security, element: <SecurityPage /> },
              { path: routes.privacySettings, element: <PrivacyPage /> },
              { path: routes.dataExport, element: <DataExportDownloadPage /> },
              { path: routes.deleteAccount, element: <DeleteAccountFlow /> },
            ],
          },
        ],
      },
      {
        element: <AuthLayout />,
        children: [
          { path: routes.register, element: <RegisterPage /> },
          { path: routes.checkEmail, element: <CheckEmailPage /> },
          { path: routes.verifyEmail, element: <VerifyEmailPage /> },
          { path: routes.login, element: <LoginPage /> },
          { path: routes.forgotPassword, element: <ForgotPasswordPage /> },
          { path: routes.resetPassword, element: <ResetPasswordPage /> },
          { path: routes.oauthCallback, element: <OAuthCallbackPage /> },
          { path: routes.revokeDevice, element: <RevokeDevicePage /> },
          { path: routes.deletionScheduled, element: <DeletionScheduledPage /> },
          { path: routes.terms, element: <LegalDocumentPage kind="terms" /> },
          { path: routes.privacy, element: <LegalDocumentPage kind="privacy" /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
      ...(import.meta.env.DEV
        ? [
            { path: routes.devMailbox, element: <DevMailboxPage /> },
            { path: routes.devOAuthAuthorize, element: <DevOAuthAuthorizePage /> },
          ]
        : []),
    ],
  },
])
