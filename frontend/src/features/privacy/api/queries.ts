import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import { privacyApi } from './privacyApi'

export function useLegalCurrent() {
  return useQuery({ queryKey: queryKeys.legalCurrent, queryFn: privacyApi.legalCurrent, staleTime: 5 * 60_000 })
}
