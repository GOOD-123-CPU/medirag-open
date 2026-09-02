import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // 仅将「所有路由都会用到」的依赖打成公共 vendor chunk；
        // echarts（仅数据大屏）与 marked/highlight.js（仅问答页）随路由懒加载自动拆分，
        // 登录页与问答页首屏不再加载图表库
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (/[\\/]node_modules[\\/](vue|vue-router|pinia|@vueuse)[\\/]/.test(id)) return 'vue-vendor'
          if (/[\\/]node_modules[\\/]@?element-plus[\\/]/.test(id)) return 'element-plus'
          return undefined
        }
      }
    }
  }
})
