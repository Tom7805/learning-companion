import 'katex/dist/katex.min.css'
import './content.css'
import type { Element, ElementContent, Root, Text } from 'hast'
import { Fragment, useMemo, type ReactNode } from 'react'
import Markdown, { type Components } from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import { cn } from '@/shared/lib/cn'
import { CodeBlock } from './CodeBlock'
import { MathBlock } from './MathBlock'
import { ResponsiveTable } from './ResponsiveTable'
import { normalizeMath, safeUrl, sanitizeSchema } from './sanitize'

/** Số trích dẫn kiểu [1] hay [1, 2] trong câu trả lời. */
const CITATION = /\[(\d{1,2}(?:\s*[,;]\s*\d{1,2})*)]/g

export interface CitationRendering {
  /** Số này có trích dẫn tương ứng không; số không có nguồn thì để nguyên chữ. */
  has: (number: number) => boolean
  /** Dựng nút trích dẫn cho một số. */
  render: (number: number) => ReactNode
}

/**
 * Bộ hiển thị nội dung có định dạng dùng chung (NCL-02-CN-003): đoạn văn, danh sách nhiều cấp, trích đoạn, công thức
 * toán trong dòng và đứng riêng, mã nguồn có tô màu cú pháp và nút sao chép, bảng cuộn ngang trong khung. Nội dung
 * luôn được làm sạch trước khi hiển thị: thẻ HTML viết thẳng bị bỏ, đường dẫn nguy hiểm bị vô hiệu (TC-03). Mọi nội
 * dung có định dạng về sau (học liệu, ghi chú…) hiển thị qua thành phần này.
 */
export function MarkdownRenderer({
  content,
  citations,
  className,
}: {
  content: string
  citations?: CitationRendering
  className?: string
}) {
  const source = useMemo(() => normalizeMath(content), [content])
  const components = useMemo(() => buildComponents(citations), [citations])
  const rehypePlugins = useMemo(
    () => [
      [rehypeSanitize, sanitizeSchema] as const,
      [rehypeHighlight, { detect: false, plainText: ['math', 'text', 'txt', 'plaintext'] }] as const,
      ...(citations ? [[rehypeCitations, citations] as const] : []),
    ],
    [citations],
  )

  return (
    <div className={cn('rich-content', className)} data-testid="rich-content">
      <Markdown
        remarkPlugins={[remarkGfm, remarkMath]}
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- bộ cắm rehype có kiểu tham số khác nhau
        rehypePlugins={rehypePlugins as any}
        skipHtml
        urlTransform={safeUrl}
        components={components}
      >
        {source}
      </Markdown>
    </div>
  )
}

function buildComponents(citations?: CitationRendering): Components {
  return {
    // Tiêu đề trong nội dung nằm dưới tiêu đề của trang: hạ hai bậc để không làm rối thứ tự tiêu đề.
    h1: ({ children }) => <h3 className="rich-heading text-lg">{children}</h3>,
    h2: ({ children }) => <h4 className="rich-heading text-base">{children}</h4>,
    h3: ({ children }) => <h5 className="rich-heading text-base">{children}</h5>,
    h4: ({ children }) => <h6 className="rich-heading text-[0.9375rem]">{children}</h6>,
    h5: ({ children }) => <h6 className="rich-heading text-[0.9375rem]">{children}</h6>,
    h6: ({ children }) => <h6 className="rich-heading text-[0.9375rem]">{children}</h6>,
    a: ({ href, children }) => (
      <a href={href} target="_blank" rel="noopener noreferrer nofollow" className="font-medium text-accent-ink underline underline-offset-2">
        {children}
      </a>
    ),
    table: ({ children }) => <ResponsiveTable>{children}</ResponsiveTable>,
    pre: ({ node, children }) => {
      const code = node?.children.find((child): child is Element => child.type === 'element' && child.tagName === 'code')
      const classes = classNames(code)
      if (classes.includes('math-display')) return <MathBlock tex={textOf(code)} display />
      const language = classes.find((name) => name.startsWith('language-'))?.slice('language-'.length)
      return (
        <CodeBlock language={language} code={textOf(code).replace(/\n$/, '')}>
          {children}
        </CodeBlock>
      )
    },
    code: ({ node, className, children }) => {
      if (classNames(node).includes('math-inline')) return <MathBlock tex={textOf(node)} />
      return <code className={className}>{children}</code>
    },
    sup: ({ node, children }) => {
      const numbers = String(node?.properties?.dataCitation ?? '')
      if (!citations || !numbers) return <sup>{children}</sup>
      return (
        <>
          {numbers.split(',').map((number) => (
            <Fragment key={number}>{citations.render(Number(number))}</Fragment>
          ))}
        </>
      )
    },
  }
}

function classNames(node?: Element): string[] {
  const value: unknown = node?.properties?.className
  return Array.isArray(value) ? value.map(String) : typeof value === 'string' ? value.split(' ') : []
}

function textOf(node?: ElementContent | Element): string {
  if (!node) return ''
  if (node.type === 'text') return node.value
  if (node.type === 'element') return node.children.map(textOf).join('')
  return ''
}

/**
 * Đổi các số trích dẫn [1], [2, 3] trong chữ thường (không phải trong mã hay công thức) thành phần tử đánh dấu để
 * {@code sup} dựng nút trích dẫn. Số không có nguồn tương ứng thì để nguyên chữ.
 */
function rehypeCitations(options: CitationRendering) {
  const skip = new Set(['code', 'pre', 'a'])
  const visit = (parent: Root | Element) => {
    const next: (typeof parent.children)[number][] = []
    for (const child of parent.children) {
      if (child.type === 'element') {
        if (!skip.has(child.tagName) && !classNames(child).some((name) => name.startsWith('katex'))) visit(child)
        next.push(child)
      } else if (child.type === 'text') {
        next.push(...splitCitations(child, options))
      } else {
        next.push(child)
      }
    }
    parent.children = next as typeof parent.children
  }
  return (tree: Root) => visit(tree)
}

function splitCitations(node: Text, options: CitationRendering): ElementContent[] {
  const parts: ElementContent[] = []
  let cursor = 0
  for (const match of node.value.matchAll(CITATION)) {
    const numbers = match[1].split(/[,;]/).map((part) => Number(part.trim()))
    if (!numbers.every((number) => options.has(number))) continue
    const start = match.index ?? 0
    if (start > cursor) parts.push({ type: 'text', value: node.value.slice(cursor, start) })
    parts.push({
      type: 'element',
      tagName: 'sup',
      properties: { dataCitation: numbers.join(',') },
      children: [{ type: 'text', value: match[0] }],
    })
    cursor = start + match[0].length
  }
  if (cursor === 0) return [node]
  if (cursor < node.value.length) parts.push({ type: 'text', value: node.value.slice(cursor) })
  return parts
}
