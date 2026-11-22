import { PINYIN_GROUPS } from './pinyin-table'

/**
 * 轻量拼音首字母索引（运行时零依赖）。
 * 使用离线生成的 CJK 静态首字母表，覆盖全部常用汉字；
 * 未收录字符原样保留其小写，不影响匹配。
 */
const CHAR_INITIAL = new Map<string, string>()
for (const [letter, chars] of Object.entries(PINYIN_GROUPS)) {
  for (const ch of chars) if (!CHAR_INITIAL.has(ch)) CHAR_INITIAL.set(ch, letter)
}

/** 取中文字符串的拼音首字母串；非中文字符保留其小写形式；跳过空白与标点。 */
export function initialsOf(text: string): string {
  let out = ''
  for (const ch of text) {
    if (/\s/.test(ch)) continue
    const initial = CHAR_INITIAL.get(ch)
    if (initial) out += initial
    else if (/[a-zA-Z0-9]/.test(ch)) out += ch.toLowerCase()
  }
  return out
}
