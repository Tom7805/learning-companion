import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/test/renderWithProviders'
import { MarkdownRenderer } from './MarkdownRenderer'
import { normalizeMath } from './sanitize'

const ANSWER = [
  'Tích phân xác định của $x^2$ trên đoạn từ 0 tới 1:',
  '',
  '$$',
  '\\int_0^1 x^2 \\, dx = \\frac{1}{3}',
  '$$',
  '',
  '```python',
  'def area(n):',
  '    return sum((i / n) ** 2 for i in range(n)) / n',
  '```',
].join('\n')

function render(content: string, citations?: Parameters<typeof MarkdownRenderer>[0]['citations']) {
  return renderWithProviders(<MarkdownRenderer content={content} citations={citations} />)
}

describe('MarkdownRenderer', () => {
  it('TC-01: công thức tích phân hiện đúng ký hiệu toán; khối mã tô màu cú pháp và sao chép được nguyên văn', async () => {
    const { user } = render(ANSWER)
    // userEvent cài bộ nhớ tạm giả khi khởi tạo: theo dõi sau khi render.
    const writeText = vi.spyOn(navigator.clipboard, 'writeText')

    const display = screen.getByTestId('math-display')
    // KaTeX dựng cả MathML: ký hiệu tích phân thật, không phải chữ "\int".
    expect(display.querySelector('math')).not.toBeNull()
    // Phần nhìn thấy là ký hiệu; mã TeX gốc chỉ nằm trong chú thích ẩn của MathML cho trình đọc màn hình.
    const visible = display.querySelector('.katex-html')?.textContent ?? ''
    expect(visible).toContain('∫')
    expect(visible).not.toContain('\\int')
    expect(screen.getByTestId('math-inline').querySelector('math')).not.toBeNull()

    const code = screen.getByTestId('code-block')
    expect(code).toHaveAttribute('data-language', 'python')
    expect(code.querySelector('.hljs-keyword')).toHaveTextContent('def')
    expect(within(code).getByRole('region', { name: 'Mã python' })).toBeInTheDocument()

    await user.click(within(code).getByTestId('code-copy'))
    expect(writeText).toHaveBeenCalledWith('def area(n):\n    return sum((i / n) ** 2 for i in range(n)) / n')
    expect(within(code).getByTestId('code-copy')).toHaveTextContent('Đã chép')
  })

  it('TC-02: bảng tám cột nằm trong khung cuộn ngang có tên, nhận tiêu điểm bàn phím', () => {
    const header = Array.from({ length: 8 }, (_, i) => `Cột ${i + 1}`)
    render([`| ${header.join(' | ')} |`, `|${' --- |'.repeat(8)}`, `| ${header.map((_, i) => `giá trị ${i}`).join(' | ')} |`].join('\n'))

    const region = screen.getByRole('region', { name: 'Bảng, cuộn ngang để xem đủ các cột' })
    expect(region).toHaveClass('overflow-x-auto')
    expect(region).toHaveAttribute('tabindex', '0')
    expect(within(region).getAllByRole('columnheader')).toHaveLength(8)
  })

  it('TC-03: thẻ chạy mã, thuộc tính sự kiện và đường dẫn javascript bị loại bỏ, phần còn lại hiển thị bình thường', () => {
    const { container } = render(
      [
        'Đoạn đầu an toàn.',
        '',
        '<script>window.hacked = true</script>',
        '',
        '<img src=x onerror="window.hacked = true"> <iframe src="https://evil.example"></iframe>',
        '',
        '[Bấm vào đây](javascript:window.hacked=true) và [trang thật](https://example.com).',
        '',
        '![ảnh theo dõi](https://tracker.example/pixel.png)',
        '',
        '**Đoạn cuối** vẫn hiển thị.',
      ].join('\n'),
    )

    expect(container.querySelector('script, iframe, img, [onerror]')).toBeNull()
    expect(container.innerHTML).not.toContain('hacked')
    expect(screen.getByText('Đoạn đầu an toàn.')).toBeInTheDocument()
    expect(screen.getByText('Bấm vào đây').closest('a')?.getAttribute('href') ?? '').not.toMatch(/javascript/i)
    expect(screen.getByRole('link', { name: 'trang thật' })).toHaveAttribute('href', 'https://example.com')
    expect(screen.getByRole('link', { name: 'trang thật' })).toHaveAttribute('rel', 'noopener noreferrer nofollow')
    expect(screen.getByText('Đoạn cuối')).toBeInTheDocument()
  })

  it('TC-04: công thức thiếu dấu đóng hiện nguyên văn kèm dấu cảnh báo, phần còn lại không bị vỡ', () => {
    render(['Trước công thức lỗi.', '', '$$', '\\frac{1}{2', '$$', '', 'Công thức đúng $a^2 + b^2 = c^2$ vẫn hiện.'].join('\n'))

    const broken = screen.getByTestId('math-error')
    expect(broken).toHaveTextContent('$$\\frac{1}{2$$')
    expect(within(broken).getByText('Công thức sai cú pháp nên hiện nguyên văn')).toHaveClass('sr-only')
    expect(screen.getByText('Trước công thức lỗi.')).toBeInTheDocument()
    expect(screen.getByTestId('math-inline').querySelector('math')).not.toBeNull()
  })

  it('danh sách nhiều cấp, danh sách đánh số và trích đoạn', () => {
    const { container } = render(['- Cấp một', '  - Cấp hai', '    - Cấp ba', '', '1. Bước một', '2. Bước hai', '', '> Trích đoạn từ tài liệu.'].join('\n'))

    expect(container.querySelectorAll('ul ul ul li')).toHaveLength(1)
    expect(container.querySelector('ol')?.children).toHaveLength(2)
    expect(container.querySelector('blockquote')).toHaveTextContent('Trích đoạn từ tài liệu.')
  })

  it('số trích dẫn trong chữ thành nút, nhưng không đổi trong mã nguồn hay số không có nguồn', async () => {
    const opened: number[] = []
    render('Theo bài giảng [1, 2], chỉ số `arr[1]` bắt đầu từ 0 [9].', {
      has: (number) => number <= 2,
      render: (number) => (
        <button type="button" data-testid="cite" onClick={() => opened.push(number)}>
          {number}
        </button>
      ),
    })

    await waitFor(() => expect(screen.getAllByTestId('cite').map((button) => button.textContent)).toEqual(['1', '2']))
    expect(screen.getByText('arr[1]').tagName).toBe('CODE')
    expect(screen.getByTestId('rich-content')).toHaveTextContent('bắt đầu từ 0 [9].')
  })
})

describe('normalizeMath', () => {
  it('đổi \\( \\) và \\[ \\] sang dấu đô la, trừ phần trong mã', () => {
    expect(normalizeMath('Ta có \\(x^2\\) và \\[\\int f\\]')).toBe('Ta có $x^2$ và \n$$\n\\int f\n$$\n')
    expect(normalizeMath('`\\(giữ\\)` rồi \\(a\\)')).toBe('`\\(giữ\\)` rồi $a$')
    expect(normalizeMath('```\n\\[giữ\\]\n```')).toBe('```\n\\[giữ\\]\n```')
  })
})
