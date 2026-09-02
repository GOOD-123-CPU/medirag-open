<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-container">
      <!-- 左侧介绍区 -->
      <div class="login-left">
        <div class="brand">
          <div class="brand-icon">
            <MediRagIcon :size="38" />
          </div>
          <h1 class="brand-name">MediRAG</h1>
        </div>
        <h2 class="tagline">基于 RAG 架构的<br />医疗知识智能问答系统</h2>
        <p class="description">
          融合多路召回、Cross-Encoder重排序与大语言模型，<br />
          为您提供有据可查、来源可溯的医疗健康知识服务。
        </p>
        <div class="feature-list">
          <div class="feature-item" v-for="f in features" :key="f.text">
            <el-icon class="feature-icon" :color="f.color"><component :is="f.icon" /></el-icon>
            <span>{{ f.text }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧登录表单 -->
      <div class="login-right">
        <div class="form-header">
          <h3>欢迎回来</h3>
          <p>登录以使用智能医疗问答服务</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              clearable
            />
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <el-button link type="primary" @click="router.push('/forgot-password')">忘记密码？</el-button>
          </div>

          <el-button
            class="login-btn"
            type="primary"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '立即登录' }}
          </el-button>
        </el-form>

        <div class="form-footer">
          <span>还没有账号？</span>
          <router-link to="/register" class="register-link">立即注册</router-link>
        </div>

        <!-- 测试账号提示 -->
        <div class="demo-accounts">
          <div class="demo-title">演示账号</div>
          <div class="demo-list">
            <div
              v-for="acc in demoAccounts"
              :key="acc.username"
              class="demo-item"
              @click="fillAccount(acc)"
            >
              <el-tag :type="acc.type as any" size="small">{{ acc.label }}</el-tag>
              <span class="demo-username">{{ acc.username }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import MediRagIcon from '@/components/MediRagIcon.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const rememberMe = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const features = [
  { icon: 'Search', color: '#60a5fa', text: '多路召回 + RRF融合检索' },
  { icon: 'Star', color: '#f59e0b', text: 'Cross-Encoder精准重排序' },
  { icon: 'Shield', color: '#10b981', text: '医疗安全兜底机制' },
  { icon: 'Document', color: '#a78bfa', text: '来源可溯，有据可查' }
]

const demoAccounts = [
  { username: 'David', password: 'David123', label: '管理员', type: 'danger' },
  { username: 'wangwei', password: 'ww1314', label: '用户', type: '' }
]

const fillAccount = (acc: { username: string; password: string }) => {
  form.username = acc.username
  form.password = acc.password
}

const handleLogin = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功，欢迎使用 MediRAG！')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    // 错误已在 request.ts 中处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #0f172a 100%);
  position: relative;
  overflow: hidden;
}

.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.06;
  background: radial-gradient(circle, #3b82f6, transparent);
}


.circle-1 { width: 600px; height: 600px; top: -200px; left: -200px; }
.circle-2 { width: 400px; height: 400px; bottom: -100px; right: -100px; background: radial-gradient(circle, #7c3aed, transparent); opacity: 0.08; }
.circle-3 { width: 200px; height: 200px; top: 40%; left: 30%; }

.login-container {
  display: flex;
  width: 900px;
  max-width: calc(100vw - 48px);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  backdrop-filter: blur(20px);
  overflow: hidden;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.5);
}

/* 左侧 */
.login-left {
  flex: 1;
  padding: 48px 40px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.2), rgba(124, 58, 237, 0.2));
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 32px;
}

.brand-icon {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.4);
}

.brand-name {
  font-size: 28px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 2px;
}

.tagline {
  font-size: 22px;
  font-weight: 700;
  color: #e2e8f0;
  line-height: 1.4;
  margin-bottom: 16px;
}

.description {
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.8;
  margin-bottom: 32px;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #cbd5e1;
  font-size: 14px;
}

.feature-icon {
  flex-shrink: 0;
}

/* 右侧 */
.login-right {
  width: 380px;
  padding: 48px 40px;
  background: rgba(255, 255, 255, 0.02);
}

.form-header {
  margin-bottom: 32px;
}

.form-header h3 {
  font-size: 24px;
  font-weight: 700;
  color: #f1f5f9;
  margin-bottom: 6px;
}

.form-header p {
  color: #64748b;
  font-size: 14px;
}

.el-input :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  box-shadow: none !important;
}

.el-input :deep(.el-input__inner) {
  color: #e2e8f0 !important;
}

.el-input :deep(.el-input__inner::placeholder) {
  color: #4b5563 !important;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -4px 0 20px;
}

.form-options :deep(.el-checkbox__label) {
  color: #94a3b8;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #2563eb, #7c3aed) !important;
  border: none !important;
  border-radius: 10px !important;
  letter-spacing: 1px;
}

.form-footer {
  text-align: center;
  margin-top: 20px;
  color: #64748b;
  font-size: 14px;
}

.register-link {
  color: #60a5fa;
  text-decoration: none;
  font-weight: 500;
  margin-left: 4px;
}
.register-link:hover { color: #93c5fd; }

.demo-accounts {
  margin-top: 24px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
}

.demo-title {
  font-size: 12px;
  color: #4b5563;
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.demo-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.demo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}
.demo-item:hover { background: rgba(255, 255, 255, 0.06); }

.demo-username {
  font-size: 13px;
  color: #94a3b8;
  font-family: monospace;
}
</style>
