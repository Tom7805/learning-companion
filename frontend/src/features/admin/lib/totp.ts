/** Khóa chia nhóm bốn ký tự cho dễ đọc và gõ tay vào ứng dụng sinh mã. */
export function groupSecret(secret: string) {
  return secret.match(/.{1,4}/g)?.join(' ') ?? secret
}
