import { defineStore } from 'pinia'

export const useUiStore = defineStore('ui', {
  state: () => ({
    aiOpen: false,
    theme: localStorage.getItem('zhixue_theme') || 'light',
  }),
  actions: {
    toggleAi() { this.aiOpen = !this.aiOpen },
    openAi() { this.aiOpen = true },
    applyTheme(t) {
      this.theme = t
      document.documentElement.setAttribute('data-theme', t)
      localStorage.setItem('zhixue_theme', t)
    },
    toggleTheme() {
      this.applyTheme(this.theme === 'dark' ? 'light' : 'dark')
    },
  },
})
