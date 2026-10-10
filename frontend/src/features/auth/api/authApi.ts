import { http } from '@/shared/api/httpClient'
import type { Account, RegisterPayload, RegistrationAccepted, ResendAccepted, SessionStatus } from '../types'

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
  async session() {
    const { data } = await http.get<SessionStatus>('/auth/session')
    return data
  },
  async logout() {
    await http.post('/auth/logout')
  },
}
