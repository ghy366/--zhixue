import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

interface User {
  id: number
  no: string
  name: string
  role: string
  avatar?: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('zhixue_token'))
  const user = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isStudent = computed(() => user.value?.role === 'student')
  const isTeacher = computed(() => user.value?.role === 'teacher')
  const isAdmin = computed(() => user.value?.role === 'admin')

  function setToken(t: string) {
    token.value = t
    localStorage.setItem('zhixue_token', t)
  }

  function setUser(u: User) {
    user.value = u
    localStorage.setItem('zhixue_user', JSON.stringify(u))
  }

  function loadFromStorage() {
    const u = localStorage.getItem('zhixue_user')
    if (u) {
      try { user.value = JSON.parse(u) } catch { /* ignore */ }
    }
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('zhixue_token')
    localStorage.removeItem('zhixue_user')
  }

  return { token, user, isLoggedIn, isStudent, isTeacher, isAdmin,
           setToken, setUser, loadFromStorage, logout }
})
