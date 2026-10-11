import { http } from '@/shared/api/httpClient'
import type {
  Account,
  DeviceSession,
  LoginPayload,
  RegisterPayload,
  RegistrationAccepted,
  ChangePasswordPayload,
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
  async changePassword(payload: ChangePasswordPayload) {
    const { data } = await http.post<{ revokedSessions: number }>('/auth/password/change', payload)
    return data
  },
}
