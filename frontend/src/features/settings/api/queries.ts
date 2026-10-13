import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { samePreferences, uiStore, type UiPreferences } from '@/app/store/uiStore'
import { queryKeys } from '@/shared/api/queryKeys'
import type { Profile, ProfileChanges } from '../types'
import { settingsApi } from './settingsApi'

export function useProfile() {
  return useQuery({ queryKey: queryKeys.profile, queryFn: settingsApi.profile, staleTime: 5 * 60_000 })
}

/**
 * Mọi thao tác trên hồ sơ trả về hồ sơ mới: ghi thẳng vào bộ nhớ đệm để giao diện đổi ngay.
 * Đổi tên hiển thị thì phiên (tên ở đầu trang) cũng cần tải lại.
 */
function useProfileMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<Profile>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (profile) => {
      const previous = queryClient.getQueryData<Profile>(queryKeys.profile)
      queryClient.setQueryData(queryKeys.profile, profile)
      if (previous?.displayName !== profile.displayName) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.session })
      }
    },
  })
}

export function useUpdateProfile() {
  return useProfileMutation(({ id, changes }: { id: string; changes: ProfileChanges }) =>
    settingsApi.updateProfile(id, changes),
  )
}

export function useUploadAvatar() {
  return useProfileMutation(({ id, image }: { id: string; image: Blob }) => settingsApi.uploadAvatar(id, image))
}

export function useRemoveAvatar() {
  return useProfileMutation((id: string) => settingsApi.removeAvatar(id))
}

/**
 * Lấy tùy chọn giao diện đã lưu theo tài khoản và áp lên máy (NCL-02-CN-001), để đổi máy vẫn thấy giao diện như
 * đã chọn. Nếu lần trước người học đổi trên máy này mà chưa lưu kịp lên tài khoản (mất mạng, tải lại trang ngay sau
 * khi bấm) thì bản trên máy mới là bản mới nhất: đẩy lên thay vì để bản cũ trên máy chủ ghi đè.
 */
export function useUiPreferencesSync(accountId: string | undefined) {
  const query = useQuery({
    queryKey: queryKeys.uiPreferences,
    queryFn: settingsApi.uiPreferences,
    enabled: Boolean(accountId),
    staleTime: Infinity,
  })
  const { mutate } = useUpdateUiPreferences(accountId)
  const pushed = useRef(false)
  useEffect(() => {
    if (!query.data || !accountId) return
    if (uiStore.pendingFor() === accountId) {
      if (pushed.current) return
      pushed.current = true
      mutate(uiStore.get())
    } else {
      uiStore.sync(query.data)
    }
  }, [query.data, accountId, mutate])
  return query
}

/**
 * Đổi tùy chọn giao diện: áp ngay trên máy rồi lưu lên tài khoản (nếu đã đăng nhập). Các lần lưu được gửi lần lượt
 * nên lần đổi sau không bao giờ bị kết quả của lần trước ghi đè; máy chủ đã có đúng bản trên máy thì bỏ cờ chưa lưu.
 */
export function useUpdateUiPreferences(accountId: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    scope: { id: 'ui-preferences' },
    onMutate: (changes: Partial<UiPreferences>) => {
      uiStore.set(changes)
      if (accountId) uiStore.markPending(accountId)
    },
    mutationFn: async (changes: Partial<UiPreferences>) =>
      accountId ? settingsApi.updateUiPreferences(changes) : uiStore.get(),
    onSuccess: (saved) => {
      if (!accountId) return
      queryClient.setQueryData(queryKeys.uiPreferences, saved)
      if (samePreferences(saved, uiStore.get())) uiStore.markSynced()
    },
  })
}
