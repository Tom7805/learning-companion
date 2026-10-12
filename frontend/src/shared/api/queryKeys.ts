export const queryKeys = {
  session: ['auth', 'session'] as const,
  deviceSessions: ['auth', 'sessions'] as const,
  legalCurrent: ['legal', 'current'] as const,
  documents: ['library', 'documents'] as const,
  profile: ['profile', 'me'] as const,
  privacyStatus: (accountId: string) => ['privacy', accountId, 'status'] as const,
  consentHistory: (accountId: string) => ['privacy', accountId, 'history'] as const,
  document: (id: string) => ['library', 'documents', id] as const,
}
