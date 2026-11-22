/**
 * bigram Dice 相似度：
 *   dice = 2 * |A ∩ B| / (|A| + |B|)
 * 对中文按字符切 bigram，长度 < 2 时退化为单字集合。
 */
export function bigrams(text: string): Set<string> {
  const chars = Array.from(text.toLowerCase().replace(/\s+/g, ''))
  const set = new Set<string>()
  if (chars.length === 1) {
    set.add(chars[0])
    return set
  }
  for (let i = 0; i < chars.length - 1; i++) {
    set.add(chars[i] + chars[i + 1])
  }
  return set
}

export function diceCoefficient(a: string, b: string): number {
  if (!a || !b) return 0
  if (a === b) return 1
  const A = bigrams(a)
  const B = bigrams(b)
  let inter = 0
  for (const g of A) if (B.has(g)) inter++
  return (2 * inter) / (A.size + B.size)
}

/**
 * 根据输入关键词，从候选词表中给出最相似的纠错建议（去重、按相似度降序）。
 */
export function suggestSimilar(
  keyword: string,
  candidates: string[],
  limit = 4,
  threshold = 0.18,
): string[] {
  const scored = new Map<string, number>()
  for (const c of candidates) {
    const s = diceCoefficient(keyword, c)
    if (s >= threshold && (!scored.has(c) || scored.get(c)! < s)) scored.set(c, s)
  }
  return [...scored.entries()]
    .sort((x, y) => y[1] - x[1])
    .slice(0, limit)
    .map(([word]) => word)
}
