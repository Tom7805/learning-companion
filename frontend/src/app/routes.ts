export const routes = {
  home: '/',
  library: '/library',
  libraryDocument: '/library/:id',
  devices: '/devices',
  security: '/security',
  privacySettings: '/privacy',
  dataExport: '/exports/:id',
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
