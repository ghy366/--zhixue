import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000  // AI 对话可能耗时较长，给 30s
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const data = res.data
    // 后端统一返回 { success, message, data }
    if (data && typeof data.success === 'boolean') {
      if (!data.success) {
        const msg = data.message || '请求失败'
        // 后端重启会清空内存 SessionStore，旧 token 会返回"登录已过期" → 清理并回登录页
        if (msg.includes('登录已过期') || msg.includes('未登录') || msg.includes('请重新登录')) {
          const auth = useAuthStore()
          auth.logout()
          if (router.currentRoute.value.path !== '/login') router.push('/login')
        }
        return Promise.reject(new Error(msg))
      }
      return data // 返回整个 ApiResponse { success, message, data }
    }
    return data
  },
  (err) => {
    if (err.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout()
      router.push('/login')
    }
    // 403 = 后端统一鉴权拦截器拒绝（角色不匹配）：透出后端的具体提示，不跳登录页
    if (err.response?.status === 403) {
      return Promise.reject(new Error(err.response?.data?.message || '权限不足'))
    }
    return Promise.reject(err)
  }
)

export default http
