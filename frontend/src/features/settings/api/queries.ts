import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
