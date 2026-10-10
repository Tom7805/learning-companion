import { z } from 'zod'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, passwordLength } from './passwordStrength'

/** Lời nhắn là khóa dịch, giao diện dịch khi hiển thị để đổi ngôn ngữ không phải dựng lại schema. */
const fields = z.object({
  displayName: z.string().trim().min(1, 'validation.displayNameRequired').max(50, 'validation.displayNameTooLong'),
  email: z
    .string()
    .trim()
    .min(1, 'validation.emailRequired')
    .max(254, 'validation.emailInvalid')
    .pipe(z.email('validation.emailInvalid')),
  password: z
    .string()
    .min(1, 'validation.passwordRequired')
    .refine((value) => passwordLength(value) >= PASSWORD_MIN_LENGTH, 'validation.passwordTooShort')
    .refine((value) => passwordLength(value) <= PASSWORD_MAX_LENGTH, 'validation.passwordTooLong'),
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
