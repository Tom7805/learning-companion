import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { DataExportStatus, OptionalPurpose, PrivacyStatus } from '../types'
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

const inProgress = (status?: DataExportStatus) => status === 'QUEUED' || status === 'RUNNING'

/** Lần xuất gần nhất; trong lúc gói đang được dựng thì hỏi lại vài giây một lần cho tới khi xong. */
export function useDataExportOverview() {
  return useQuery({
    queryKey: queryKeys.dataExports,
    queryFn: privacyApi.exportOverview,
    refetchInterval: (query) => (inProgress(query.state.data?.latest?.status) ? 2_000 : false),
  })
}

export function useDataExport(id: string) {
  return useQuery({
    queryKey: queryKeys.dataExport(id),
    queryFn: () => privacyApi.dataExport(id),
    refetchInterval: (query) => (inProgress(query.state.data?.status) ? 2_000 : false),
  })
}

export function useRequestDataExport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: privacyApi.requestExport,
    onSuccess: (overview) => {
      queryClient.setQueryData(queryKeys.dataExports, overview)
    },
  })
}

export function useDownloadDataExport(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (password?: string) => privacyApi.downloadExport(id, password),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.dataExport(id) }),
  })
}
