export type AssistantLanguage = 'vi' | 'en'

/** Hồ sơ và tùy chọn học tập của chính người học (NCL-01-CN-005). */
export interface Profile {
  id: string
  displayName: string
  email: string
  /** Mã múi giờ IANA; trống nghĩa là chưa chọn, giao diện dùng múi giờ của thiết bị. */
  timeZone?: string | null
  assistantLanguage: AssistantLanguage
  dailyGoalMinutes: number
  dayRolloverHour: number
  avatarUrl?: string | null
  updatedAt: string
}

export type ProfileChanges = Partial<
  Pick<Profile, 'displayName' | 'assistantLanguage' | 'dailyGoalMinutes' | 'dayRolloverHour'> & { timeZone: string }
>
