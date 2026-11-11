import { defaultUrlTransform } from 'react-markdown'
import { defaultSchema, type Options as SanitizeSchema } from 'rehype-sanitize'

/**
 * Lược đồ làm sạch nội dung có định dạng (NCL-02-CN-003 TC-03): dựa trên lược đồ an toàn mặc định (kiểu GitHub), chỉ
 * cho thêm lớp đánh dấu công thức toán của remark-math. Thẻ HTML viết thẳng trong nội dung đã bị bỏ trước bước này
 * ({@code skipHtml}), nên thẻ chạy mã, khung nhúng hay thuộc tính sự kiện không bao giờ tới được trình duyệt.
 */
export const sanitizeSchema: SanitizeSchema = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    // Thay hẳn khai báo của thẻ code (mặc định chỉ cho language-*) để giữ được lớp đánh dấu công thức.
    code: [['className', /^language-./, 'math-inline', 'math-display']],
  },
  // Ảnh trong câu trả lời có thể là điểm theo dõi từ trang lạ: không hiển thị.
  tagNames: (defaultSchema.tagNames ?? []).filter((tag) => tag !== 'img'),
}

/** Chỉ giữ đường dẫn http, https, mailto và đường dẫn trong ứng dụng; javascript:, data:… thành rỗng. */
export const safeUrl = defaultUrlTransform

const CODE = /(```[\s\S]*?(?:```|$)|~~~[\s\S]*?(?:~~~|$)|`[^`\n]*`)/

/**
 * Mô hình hay viết công thức bằng \( … \) và \[ … \]; remark-math chỉ hiểu $ … $ và $$ … $$. Đổi cách viết đó sang dấu
 * đô la, trừ phần nằm trong mã nguồn để không đụng tới mã của người học.
 */
export function normalizeMath(text: string) {
  return text
    .split(CODE)
    .map((part, index) =>
      index % 2 === 1
        ? part
        : part
            .replace(/\\\[([\s\S]+?)\\\]/g, (_, tex: string) => `\n$$\n${tex.trim()}\n$$\n`)
            .replace(/\\\(([\s\S]+?)\\\)/g, (_, tex: string) => `$${tex.trim()}$`),
    )
    .join('')
}
