export const queryKeys = {
  session: ['auth', 'session'] as const,
  deviceSessions: ['auth', 'sessions'] as const,
  legalCurrent: ['legal', 'current'] as const,
  documents: ['library', 'documents'] as const,
  profile: ['profile', 'me'] as const,
  oauthProviders: ['auth', 'oauth', 'providers'] as const,
  linkedIdentities: (accountId: string) => ['auth', 'linked', accountId] as const,
  privacyStatus: (accountId: string) => ['privacy', accountId, 'status'] as const,
  consentHistory: (accountId: string) => ['privacy', accountId, 'history'] as const,
  dataExports: ['exports'] as const,
  dataExport: (id: string) => ['exports', id] as const,
  document: (id: string) => ['library', 'documents', id] as const,
}
