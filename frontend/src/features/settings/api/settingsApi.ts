import type { UiPreferences } from '@/app/store/uiStore'
import { http } from '@/shared/api/httpClient'
import type { Profile, ProfileChanges } from '../types'

/** Máy chủ dùng hằng viết hoa (DARK, LARGE); giao diện dùng chữ thường. */
interface ServerUiPreferences {
  themeMode: string
  fontSize: string
  sidebarCollapsed: boolean
}

const fromServer = (data: ServerUiPreferences): UiPreferences => ({
  themeMode: data.themeMode.toLowerCase() as UiPreferences['themeMode'],
  fontSize: data.fontSize.toLowerCase() as UiPreferences['fontSize'],
  sidebarCollapsed: data.sidebarCollapsed,
})

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
  async uiPreferences() {
    const { data } = await http.get<ServerUiPreferences>('/ui-preferences')
    return fromServer(data)
  },
  async updateUiPreferences(changes: Partial<UiPreferences>) {
    const { data } = await http.patch<ServerUiPreferences>('/ui-preferences', {
      themeMode: changes.themeMode?.toUpperCase(),
      fontSize: changes.fontSize?.toUpperCase(),
      sidebarCollapsed: changes.sidebarCollapsed,
    })
    return fromServer(data)
  },
  async removeAvatar(id: string) {
    const { data } = await http.delete<Profile>(`/profiles/${encodeURIComponent(id)}/avatar`)
    return data
  },
}
