import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import { sprite } from './assets/sprite'
import './assets/legacy.css'
import './assets/vue.css'
import './assets/bridge.css'

// 注入原 SVG 图标 sprite（供 <use href="#i-xxx"> 引用）
document.body.insertAdjacentHTML('afterbegin', sprite)

createApp(App).use(createPinia()).use(router).mount('#app')
