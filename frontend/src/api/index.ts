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
        return Promise.reject(new Error(data.message || '请求失败'))
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
    return Promise.reject(err)
  }
)

export default http
