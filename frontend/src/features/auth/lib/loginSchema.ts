import { z } from 'zod'

/** Đăng nhập chỉ kiểm định dạng; quy tắc độ dài mật khẩu thuộc lúc đặt mật khẩu, không phải lúc nhập lại. */
export const loginSchema = z.object({
  email: z.string().trim().min(1, 'validation.emailRequired').pipe(z.email('validation.emailInvalid')),
  password: z.string().min(1, 'validation.passwordRequired'),
  rememberDevice: z.boolean(),
})

export type LoginFormValues = z.infer<typeof loginSchema>
