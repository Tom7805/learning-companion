export const routes = {
  home: '/',
  library: '/library',
  libraryDocument: '/library/:id',
  devices: '/devices',
  security: '/security',
  privacySettings: '/privacy',
  register: '/register',
  checkEmail: '/register/check-email',
  verifyEmail: '/verify-email',
  login: '/login',
  forgotPassword: '/forgot-password',
  resetPassword: '/reset-password',
  revokeDevice: '/devices/revoke',
  terms: '/legal/terms',
  privacy: '/legal/privacy',
  devMailbox: '/dev/mailbox',
} as const

export const documentPath = (id: string) => `/library/${encodeURIComponent(id)}`
