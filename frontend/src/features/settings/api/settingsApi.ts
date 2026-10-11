import { http } from '@/shared/api/httpClient'
import type { Profile, ProfileChanges } from '../types'

export const settingsApi = {
  async profile() {
    const { data } = await http.get<Profile>('/profiles/me')
    return data
  },
  async updateProfile(id: string, changes: ProfileChanges) {
    const { data } = await http.patch<Profile>(`/profiles/${encodeURIComponent(id)}`, changes)
    return data
  },
  async uploadAvatar(id: string, image: Blob) {
    const form = new FormData()
    form.append('file', image, 'avatar.jpg')
    const { data } = await http.put<Profile>(`/profiles/${encodeURIComponent(id)}/avatar`, form)
    return data
  },
  async removeAvatar(id: string) {
    const { data } = await http.delete<Profile>(`/profiles/${encodeURIComponent(id)}/avatar`)
    return data
  },
}
