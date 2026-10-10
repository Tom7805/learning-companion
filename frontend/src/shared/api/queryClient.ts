import { QueryClient } from '@tanstack/react-query'

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 },
      mutations: { retry: false },
    },
  })
}
