// 复刻原 app.js 的 api()：统一加 /api 前缀、注入 token、会话失效处理
const BASE = import.meta.env.VITE_API_BASE || ''
const TOKEN_KEY = 'zhixue_token'
const USER_KEY = 'zhixue_user'

export function getToken() { return localStorage.getItem(TOKEN_KEY) }
export function setToken(t) { localStorage.setItem(TOKEN_KEY, t) }
export function getUser() {
  try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch { return null }
}
export function setUser(u) { localStorage.setItem(USER_KEY, JSON.stringify(u)) }
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export async function api(method, path, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } }
  const token = getToken()
  if (token) opts.headers['Authorization'] = 'Bearer ' + token
  if (body !== undefined) opts.body = JSON.stringify(body)
  let res
  try {
    res = await fetch(BASE + '/api' + path, opts)
  } catch (e) {
    return { success: false, message: '网络异常，请稍后再试' }
  }
  let data
  try { data = await res.json() } catch { data = { success: false, message: '服务器错误 (' + res.status + ')' } }
  const msg = data && data.message ? data.message : ''
  if (msg.indexOf('未登录') >= 0 || msg.indexOf('登录已失效') >= 0) {
    clearAuth()
    window.dispatchEvent(new CustomEvent('auth-expired'))
    setTimeout(() => location.reload(), 1200)
  }
  return data
}
