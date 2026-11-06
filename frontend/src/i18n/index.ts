import { ref } from 'vue'
import { en } from './dicts'

/**
 * 轻量 i18n：「中文即 key」模式
 * - t('首页')               → 中文环境直接返回 '首页'
 * - t('首页')               → 英文环境查 dicts 得 'Home'，缺条目回落中文
 * - t('已选 {n} 门', { n }) → 支持 {x} 插值
 * locale 是 ref：模板里直接调用 t() 会建立响应依赖，切换语言全站即时刷新
 */
export type Lang = 'zh' | 'en'
const STORAGE_KEY = 'zhixue_locale'
const locale = ref<Lang>((localStorage.getItem(STORAGE_KEY) as Lang) || 'zh')

function interpolate(s: string, params?: Record<string, any>): string {
  if (!params) return s
  return s.replace(/\{(\w+)\}/g, (_, k) =>
    params[k] != null ? String(params[k]) : `{${k}}`)
}

export function t(key: string, params?: Record<string, any>): string {
  const raw = locale.value === 'zh' ? key : (en[key] ?? key)
  return interpolate(raw, params)
}

export function getLocale(): Lang { return locale.value }
export function setLocale(l: Lang) {
  locale.value = l
  localStorage.setItem(STORAGE_KEY, l)
  document.documentElement.lang = l === 'zh' ? 'zh-CN' : 'en'
}
export function toggleLocale() { setLocale(locale.value === 'zh' ? 'en' : 'zh') }

/** 初始化 html lang（main.ts 调用一次） */
export function initLocale() { document.documentElement.lang = locale.value === 'zh' ? 'zh-CN' : 'en' }
