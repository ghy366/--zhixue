import { defineStore } from 'pinia'
import { getToken, setToken, getUser, setUser, clearAuth, api } from '@/api/client'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: getUser(),
  }),
  getters: {
    isLoggedIn: (s) => !!s.token && !!s.user,
    role: (s) => (s.user ? s.user.role : null),
  },
  actions: {
    async login(no, password) {
      const r = await api('POST', '/auth/login', { no, password })
      if (r.success) {
        setToken(r.data.token)
        setUser(r.data.user)
        this.token = r.data.token
        this.user = r.data.user
      }
      return r
    },
    async register(data) {
      return api('POST', '/auth/register', data)
    },
    /** 资料更新后同步本地 user（顶栏头像/姓名即时刷新） */
    updateUser(patch) {
      this.user = Object.assign({}, this.user || {}, patch)
      setUser(this.user)
    },
    logout() {
      api('POST', '/auth/logout').catch(() => {})
      clearAuth()
      this.token = null
      this.user = null
    },
  },
})
