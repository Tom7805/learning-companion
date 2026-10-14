import { lazy, Suspense, type ComponentType } from 'react'
import { createBrowserRouter } from 'react-router'
import { AppShell } from '@/layouts/AppShell'
import { AuthLayout } from '@/layouts/AuthLayout'
import { ErrorPage } from '@/pages/ErrorPage'
import { ProtectedRoute } from './ProtectedRoute'
import { Root } from './Root'
import { routes } from './routes'

/**
 * Mỗi trang là một gói mã riêng, chỉ tải khi mở tới (NCL-02-CN-001): lần đầu mở ứng dụng chỉ cần phần khung, các
 * trang còn lại được service worker lưu đệm khi đã mở một lần.
 */
function page<K extends string, P extends object>(load: () => Promise<Record<K, ComponentType<P>>>, name: K) {
  return lazy(async () => ({ default: (await load())[name] }))
}

const DashboardPage = page(() => import('@/features/dashboard/pages/DashboardPage'), 'DashboardPage')
const LibraryPage = page(() => import('@/features/library/pages/LibraryPage'), 'LibraryPage')
const DocumentPage = page(() => import('@/features/library/pages/DocumentPage'), 'DocumentPage')
const DevicesPage = page(() => import('@/features/auth/pages/DevicesPage'), 'DevicesPage')
const SecurityPage = page(() => import('@/features/auth/pages/SecurityPage'), 'SecurityPage')
const PrivacyPage = page(() => import('@/features/privacy/pages/PrivacyPage'), 'PrivacyPage')
const DataExportDownloadPage = page(
  () => import('@/features/privacy/pages/DataExportDownloadPage'),
  'DataExportDownloadPage',
)
const DeleteAccountFlow = page(() => import('@/features/privacy/pages/DeleteAccountFlow'), 'DeleteAccountFlow')
const NotificationOpenPage = page(
  () => import('@/features/notifications/pages/NotificationOpenPage'),
  'NotificationOpenPage',
)
const ContentGonePage = page(() => import('@/pages/ContentGonePage'), 'ContentGonePage')
const RegisterPage = page(() => import('@/features/auth/pages/RegisterPage'), 'RegisterPage')
const CheckEmailPage = page(() => import('@/features/auth/pages/CheckEmailPage'), 'CheckEmailPage')
const VerifyEmailPage = page(() => import('@/features/auth/pages/VerifyEmailPage'), 'VerifyEmailPage')
const LoginPage = page(() => import('@/features/auth/pages/LoginPage'), 'LoginPage')
const ForgotPasswordPage = page(() => import('@/features/auth/pages/ForgotPasswordPage'), 'ForgotPasswordPage')
const ResetPasswordPage = page(() => import('@/features/auth/pages/ResetPasswordPage'), 'ResetPasswordPage')
const OAuthCallbackPage = page(() => import('@/features/auth/pages/OAuthCallbackPage'), 'OAuthCallbackPage')
const RevokeDevicePage = page(() => import('@/features/auth/pages/RevokeDevicePage'), 'RevokeDevicePage')
const DeletionScheduledPage = page(
  () => import('@/features/privacy/pages/DeletionScheduledPage'),
  'DeletionScheduledPage',
)
const LegalDocumentPage = page(() => import('@/features/privacy/pages/LegalDocumentPage'), 'LegalDocumentPage')
const NotFoundPage = page(() => import('@/pages/NotFoundPage'), 'NotFoundPage')

// Công cụ chỉ có khi chạy "npm run dev"; bản dựng thật không chứa mã của chúng.
function devRoutes() {
  const DevMailboxPage = page(() => import('@/dev/DevMailboxPage'), 'DevMailboxPage')
  const DevOAuthAuthorizePage = page(() => import('@/dev/DevOAuthAuthorizePage'), 'DevOAuthAuthorizePage')
  return [
    {
      path: routes.devMailbox,
      element: (
        <Suspense fallback={null}>
          <DevMailboxPage />
        </Suspense>
      ),
    },
    {
      path: routes.devOAuthAuthorize,
      element: (
        <Suspense fallback={null}>
          <DevOAuthAuthorizePage />
        </Suspense>
      ),
    },
  ]
}

export const router = createBrowserRouter([
  {
    element: <Root />,
    errorElement: <ErrorPage />,
    children: [
      {
        element: <ProtectedRoute />,
        children: [
          {
            element: <AppShell />,
            children: [
              {
                // Trang lỗi hiện trong vùng nội dung, thanh bên vẫn dùng được để đi trang khác.
                errorElement: <ErrorPage />,
                children: [
                  { path: routes.home, element: <DashboardPage /> },
                  { path: routes.library, element: <LibraryPage /> },
                  { path: routes.libraryDocument, element: <DocumentPage /> },
                  { path: routes.devices, element: <DevicesPage /> },
                  { path: routes.security, element: <SecurityPage /> },
                  { path: routes.privacySettings, element: <PrivacyPage /> },
                  { path: routes.dataExport, element: <DataExportDownloadPage /> },
                  { path: routes.deleteAccount, element: <DeleteAccountFlow /> },
                  { path: routes.notification, element: <NotificationOpenPage /> },
                  { path: routes.contentGone, element: <ContentGonePage /> },
                ],
              },
            ],
          },
        ],
      },
      {
        element: <AuthLayout />,
        children: [
          {
            errorElement: <ErrorPage />,
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
        ],
      },
      ...(import.meta.env.DEV ? devRoutes() : []),
    ],
  },
])
