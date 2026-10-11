import { z } from 'zod'
import { emailField, newPasswordField } from './passwordSchema'

/** Lời nhắn là khóa dịch, giao diện dịch khi hiển thị để đổi ngôn ngữ không phải dựng lại schema. */
const fields = z.object({
  displayName: z.string().trim().min(1, 'validation.displayNameRequired').max(50, 'validation.displayNameTooLong'),
  email: emailField,
  password: newPasswordField,
  confirmPassword: z.string().min(1, 'validation.confirmRequired'),
  acceptTerms: z.boolean().refine((value) => value, 'validation.termsRequired'),
})

const passwordPair = fields.pick({ password: true, confirmPassword: true })

export const registerSchema = fields.refine((values) => values.password === values.confirmPassword, {
  path: ['confirmPassword'],
  message: 'validation.confirmMismatch',
  // Báo không khớp ngay cả khi trường khác còn lỗi, miễn hai ô mật khẩu đã có giá trị.
  when: (payload) => passwordPair.safeParse(payload.value).success,
})

export type RegisterFormInput = z.input<typeof registerSchema>
export type RegisterFormValues = z.output<typeof registerSchema>
