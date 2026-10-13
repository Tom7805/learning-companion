/**
 * Bỏ dấu tiếng Việt và viết thường để tìm kiếm không phân biệt dấu: "Định luật Ôm" và "dinh luat om" khớp nhau.
 * Chữ "đ" không phải chữ có dấu kết hợp nên được đổi riêng.
 */
export function foldVietnamese(text: string) {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim()
}

/** Mọi từ trong câu tìm đều có mặt trong văn bản (không phân biệt dấu, không theo thứ tự). */
export function matchesSearch(text: string, query: string) {
  const haystack = foldVietnamese(text)
  return foldVietnamese(query)
    .split(/\s+/)
    .filter(Boolean)
    .every((word) => haystack.includes(word))
}
