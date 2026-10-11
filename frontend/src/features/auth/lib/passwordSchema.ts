import { z } from 'zod'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, passwordLength } from './passwordStrength'

/** Quy tắc cho mọi mật khẩu mới: đăng ký, đặt lại qua thư và đổi mật khẩu dùng chung một nguồn. */
export const newPasswordField = z
  .string()
  .min(1, 'validation.passwordRequired')
  .refine((value) => passwordLength(value) >= PASSWORD_MIN_LENGTH, 'validation.passwordTooShort')
  .refine((value) => passwordLength(value) <= PASSWORD_MAX_LENGTH, 'validation.passwordTooLong')

export const emailField = z
  .string()
  .trim()
  .min(1, 'validation.emailRequired')
  .max(254, 'validation.emailInvalid')
  .pipe(z.email('validation.emailInvalid'))

/** Biểu mẫu đặt lại mật khẩu từ đường dẫn trong thư. */
const resetFields = z.object({
  password: newPasswordField,
  confirmPassword: z.string().min(1, 'validation.confirmRequired'),
})

export const resetPasswordSchema = resetFields.refine((values) => values.password === values.confirmPassword, {
  path: ['confirmPassword'],
  message: 'validation.confirmMismatch',
  when: (payload) => resetFields.safeParse(payload.value).success,
})

export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>

/** Biểu mẫu đổi mật khẩu khi đang đăng nhập. */
const changeFields = z.object({
  currentPassword: z.string().min(1, 'validation.currentPasswordRequired'),
  newPassword: newPasswordField,
  confirmPassword: z.string().min(1, 'validation.confirmRequired'),
})

const newPair = changeFields.pick({ newPassword: true, confirmPassword: true })
const currentAndNew = changeFields.pick({ currentPassword: true, newPassword: true })

export const changePasswordSchema = changeFields
  .refine((values) => values.newPassword !== values.currentPassword, {
    path: ['newPassword'],
    message: 'validation.passwordSameAsCurrent',
    when: (payload) => currentAndNew.safeParse(payload.value).success,
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'validation.confirmMismatch',
    when: (payload) => newPair.safeParse(payload.value).success,
  })

export type ChangePasswordValues = z.infer<typeof changePasswordSchema>
