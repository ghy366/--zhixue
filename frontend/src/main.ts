import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

// 原版 CSS（覆盖 sidebar / nav / stat-card / course-card 等所有视觉样式）
import './styles/original.css'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { setupCardTilt } from './utils/cardTilt'
import { setupGlassGlow } from './utils/glassGlow'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

// 课程卡片悬浮 3D 倾斜（全局事件委托）
setupCardTilt()
// 玻璃卡片鼠标跟随液态高光（写 CSS 变量，rAF 节流）
setupGlassGlow()

// ⚠️ 关键：应用启动时从 localStorage 恢复用户信息（否则 auth.user = null）
const auth = useAuthStore()
auth.loadFromStorage()

// 全局 svgIcon 函数 —— 原版 app.js 用来渲染 SVG symbol 图标的工具
// 用法: svgIcon('home')  →  <svg class="icon"><use href="#i-home"/></svg>
app.config.globalProperties.svgIcon = (name: string) =>
  `<svg class="icon"><use href="#i-${name}"/></svg>`

app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
