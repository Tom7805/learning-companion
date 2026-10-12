import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { OptionalPurpose, PrivacyStatus } from '../types'
import { privacyApi } from './privacyApi'

export function useLegalCurrent() {
  return useQuery({ queryKey: queryKeys.legalCurrent, queryFn: privacyApi.legalCurrent, staleTime: 5 * 60_000 })
}

export function usePrivacyStatus(accountId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.privacyStatus(accountId ?? ''),
    queryFn: () => privacyApi.status(accountId!),
    enabled: Boolean(accountId),
    staleTime: 60_000,
  })
}

export function useConsentHistory(accountId: string) {
  return useQuery({ queryKey: queryKeys.consentHistory(accountId), queryFn: () => privacyApi.history(accountId) })
}

/** Mỗi thay đổi đồng ý trả về trạng thái mới; lịch sử cũng cần tải lại. */
function usePrivacyMutation<TVariables>(accountId: string, mutationFn: (variables: TVariables) => Promise<PrivacyStatus>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (status) => {
      queryClient.setQueryData(queryKeys.privacyStatus(accountId), status)
      void queryClient.invalidateQueries({ queryKey: queryKeys.consentHistory(accountId) })
    },
  })
}

export function useSetConsent(accountId: string) {
  return usePrivacyMutation(accountId, ({ purpose, granted }: { purpose: OptionalPurpose; granted: boolean }) =>
    privacyApi.setConsent(accountId, purpose, granted),
  )
}

export function useAcceptTerms(accountId: string) {
  return usePrivacyMutation(accountId, ({ terms, privacy }: { terms: string; privacy: string }) =>
    privacyApi.acceptTerms(accountId, terms, privacy),
  )
}
