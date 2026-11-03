export const routes = {
  home: '/',
  library: '/library',
  libraryDocument: '/library/:id',
  devices: '/devices',
  security: '/security',
  privacySettings: '/privacy',
  dataExport: '/exports/:id',
  deleteAccount: '/account/delete',
  notification: '/notifications/:id',
  contentGone: '/gone',
  /** Lượt trí tuệ nhân tạo và dung lượng của người học (NCL-04-CN-003). */
  usage: '/usage',
  admin: '/admin',
  adminAudit: '/admin/audit',
  adminAi: '/admin/ai',
  adminAiConfig: '/admin/ai-config',
  adminQuotas: '/admin/quotas',
  /** Bước mã xác thực thứ hai khi quản trị viên đăng nhập; chưa có phiên nên nằm ngoài khu quản trị. */
  adminTwoFactor: '/login/two-factor',
  deletionScheduled: '/account/deletion-scheduled',
  register: '/register',
  checkEmail: '/register/check-email',
  verifyEmail: '/verify-email',
  login: '/login',
  forgotPassword: '/forgot-password',
  resetPassword: '/reset-password',
  oauthCallback: '/auth/callback/:provider',
  revokeDevice: '/devices/revoke',
  terms: '/legal/terms',
  privacy: '/legal/privacy',
  devMailbox: '/dev/mailbox',
  devOAuthAuthorize: '/dev/oauth/authorize',
} as const

export const documentPath = (id: string) => `/library/${encodeURIComponent(id)}`
export const dataExportPath = (id: string) => `/exports/${encodeURIComponent(id)}`
export const notificationPath = (id: string) => `/notifications/${encodeURIComponent(id)}`
