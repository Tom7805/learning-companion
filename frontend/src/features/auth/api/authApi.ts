import { http } from '@/shared/api/httpClient'
import type {
  Account,
  DeviceSession,
  LoginPayload,
  RegisterPayload,
  RegistrationAccepted,
  ChangePasswordPayload,
  LinkedIdentity,
  OAuthProvider,
  OAuthResult,
  ResendAccepted,
  ResetPasswordPayload,
  RevokedByLink,
  SessionStatus,
} from '../types'

export const authApi = {
  async register(payload: RegisterPayload) {
    const { data } = await http.post<RegistrationAccepted>('/auth/register', payload)
    return data
  },
  async verifyEmail(token: string) {
    const { data } = await http.post<Account>('/auth/verify-email', { token })
    return data
  },
  async resendVerification(input: { email: string } | { token: string }) {
    const { data } = await http.post<ResendAccepted>('/auth/verify-email/resend', input)
    return data
  },
  async login(payload: LoginPayload) {
    const { data } = await http.post<Account>('/auth/login', payload)
    return data
  },
  async session() {
    const { data } = await http.get<SessionStatus>('/auth/session')
    return data
  },
  async logout() {
    await http.post('/auth/logout')
  },
  async sessions() {
    const { data } = await http.get<DeviceSession[]>('/auth/sessions')
    return data
  },
  async revokeSession(id: string) {
    await http.delete(`/auth/sessions/${id}`)
  },
  async revokeOtherSessions() {
    const { data } = await http.post<{ revokedCount: number }>('/auth/sessions/revoke-others')
    return data
  },
  async revokeByLink(token: string) {
    const { data } = await http.post<RevokedByLink>('/auth/sessions/revoke-link', { token })
    return data
  },
  /** Luôn được chấp nhận như nhau, dù địa chỉ có tài khoản hay không. */
  async forgotPassword(input: { email: string } | { token: string }) {
    const { data } = await http.post<ResendAccepted>('/auth/password/forgot', input)
    return data
  },
  async checkResetToken(token: string) {
    await http.post('/auth/password/reset/check', { token })
  },
  async resetPassword(payload: ResetPasswordPayload) {
    const { data } = await http.post<{ email: string }>('/auth/password/reset', payload)
    return data
  },
  /** Đặt mật khẩu lần đầu cho tài khoản chỉ đăng nhập qua nhà cung cấp định danh. */
  async setPassword(newPassword: string) {
    await http.post('/auth/password/set', { newPassword })
  },
  async oauthProviders() {
    const { data } = await http.get<OAuthProvider[]>('/auth/oauth/providers')
    return data
  },
  async oauthStart(provider: string, input: { mode: 'LOGIN' | 'LINK'; rememberDevice?: boolean; returnTo?: string }) {
    const { data } = await http.post<{ authorizationUrl: string }>(`/auth/oauth/${provider}/start`, input)
    return data.authorizationUrl
  },
  async oauthCallback(provider: string, input: { code?: string | null; state?: string | null; error?: string | null }) {
    const { data } = await http.post<OAuthResult>(`/auth/oauth/${provider}/callback`, input)
    return data
  },
  async oauthSignup(input: {
    token: string
    displayName: string
    acceptTerms: boolean
    termsVersion: string
    privacyVersion: string
  }) {
    const { data } = await http.post<OAuthResult>('/auth/oauth/signup', input)
    return data
  },
  async oauthLink(token: string, password: string) {
    const { data } = await http.post<OAuthResult>('/auth/oauth/link', { token, password })
    return data
  },
  async linkedIdentities(accountId: string) {
    const { data } = await http.get<LinkedIdentity[]>(`/accounts/${encodeURIComponent(accountId)}/linked-identities`)
    return data
  },
  async unlinkIdentity(accountId: string, identityId: string) {
    await http.delete(`/accounts/${encodeURIComponent(accountId)}/linked-identities/${encodeURIComponent(identityId)}`)
  },
  async changePassword(payload: ChangePasswordPayload) {
    const { data } = await http.post<{ revokedSessions: number }>('/auth/password/change', payload)
    return data
  },
}
