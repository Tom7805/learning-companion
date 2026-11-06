import type { DocumentChunk } from '../../types'

export function findMatches(chunks: DocumentChunk[], term: string) {
  const needle = term.trim().toLocaleLowerCase()
  if (!needle) return []
  return chunks.flatMap((chunk) => {
    const text = chunk.content.toLocaleLowerCase()
    const matches: { chunk: DocumentChunk; offset: number }[] = []
    let offset = 0
    while ((offset = text.indexOf(needle, offset)) !== -1) {
      matches.push({ chunk, offset })
      offset += needle.length
    }
    return matches
  })
}
