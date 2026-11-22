import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, 'src')
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        // 8080 被其他应用占用时可用 VITE_API_TARGET 指定后端端口
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      },
      // 聊天图片/附件直链（后端静态映射），开发模式下同样代理到后端
      '/files': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: path.resolve(import.meta.dirname, '../src/main/resources/static-vue'),
    emptyOutDir: true    // 每次构建清空 outDir（目录在 frontend 根之外，vite 默认不清空）：旧 hash 资源残留会让 jar 随构建次数无限膨胀
  }
})
