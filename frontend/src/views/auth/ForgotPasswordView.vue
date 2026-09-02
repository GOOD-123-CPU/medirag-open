<template>
  <div class="forgot-page">
    <div class="bg-decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
    </div>

    <div class="forgot-card">
      <!-- 顶部 Logo -->
      <div class="card-header">
        <div class="logo">
          <div class="logo-icon">
            <MediRagIcon :size="24" />
          </div>
          <span class="logo-text">MediRAG</span>
        </div>
        <h2>找回密码</h2>
        <p>通过手机验证码重置您的账号密码</p>
      </div>

      <!-- 步骤条 -->
      <el-steps :active="step" align-center class="steps" finish-status="success">
        <el-step title="验证手机号" />
        <el-step title="重置密码" />
        <el-step title="完成" />
      </el-steps>

      <!-- Step 1: 发送验证码 -->
      <el-form
        v-if="step === 0"
        ref="step1Ref"
        :model="form"
        :rules="step1Rules"
        size="large"
        label-position="top"
      >
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="请输入注册时绑定的手机号"
            :prefix-icon="Phone"
            clearable
            maxlength="11"
          />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <div class="code-row">
            <el-input
              v-model="form.code"
              placeholder="请输入6位验证码"
              :prefix-icon="Key"
              clearable
              maxlength="6"
            />
            <el-button
              class="send-btn"
              :disabled="countdown > 0 || sendLoading"
              :loading="sendLoading"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重试` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <!-- 模拟提示 -->
        <div v-if="mockCode" class="mock-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>【模拟验证码】{{ mockCode }}（实际项目中由短信下发）</span>
        </div>

        <el-button
          class="action-btn"
          type="primary"
          :loading="verifyLoading"
          @click="handleVerifyCode"
        >
          下一步
        </el-button>
      </el-form>

      <!-- Step 2: 设置新密码 -->
      <el-form
        v-else-if="step === 1"
        ref="step2Ref"
        :model="form"
        :rules="step2Rules"
        size="large"
        label-position="top"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="请输入新密码（6-32个字符）"
            :prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入新密码"
            :prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <el-button
          class="action-btn"
          type="primary"
          :loading="resetLoading"
          @click="handleResetPassword"
        >
          确认重置
        </el-button>
      </el-form>

      <!-- Step 3: 完成 -->
      <div v-else class="success-state">
        <div class="success-icon">
          <el-icon size="48" color="#10b981"><CircleCheckFilled /></el-icon>
        </div>
        <h3>密码重置成功</h3>
        <p>您的密码已更新，请使用新密码登录。</p>
        <el-button class="action-btn" type="primary" @click="router.push('/login')">
          立即登录
        </el-button>
      </div>

      <div class="card-footer">
        <router-link to="/login" class="back-link">
          <el-icon><ArrowLeft /></el-icon> 返回登录
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Phone, Key, Lock, InfoFilled, CircleCheckFilled, ArrowLeft } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'
import MediRagIcon from '@/components/MediRagIcon.vue'

const router = useRouter()

const step = ref(0)
const sendLoading = ref(false)
const verifyLoading = ref(false)
const resetLoading = ref(false)
const countdown = ref(0)
const mockCode = ref('')

const step1Ref = ref<FormInstance>()
const step2Ref = ref<FormInstance>()

const form = reactive({
  phone: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const step1Rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码只能为数字', trigger: 'blur' }
  ]
}

const validateConfirmPwd = (_rule: any, value: string, callback: any) => {
  if (value !== form.newPassword) {
    callback(new Error('两次密码不一致'))
  } else {
    callback()
  }
}

const step2Rules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为6-32个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPwd, trigger: 'blur' }
  ]
}

let timer: ReturnType<typeof setInterval> | null = null

const startCountdown = () => {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer!)
      timer = null
    }
  }, 1000)
}

const handleSendCode = async () => {
  // 单独校验手机号字段
  await step1Ref.value?.validateField('phone').catch(() => { throw new Error() })

  sendLoading.value = true
  try {
    await userApi.sendResetCode(form.phone)
    ElMessage.success('验证码已发送，请注意查收（控制台查看模拟验证码）')
    startCountdown()
    // 开发环境提示：从后端日志中读取验证码
    mockCode.value = '请查看后端控制台日志中的验证码'
  } catch {
    // 错误已由 request.ts 统一处理
  } finally {
    sendLoading.value = false
  }
}

const handleVerifyCode = async () => {
  const valid = await step1Ref.value?.validate().catch(() => false)
  if (!valid) return
  // 直接进入下一步，实际验证在重置密码时由后端完成
  step.value = 1
}

const handleResetPassword = async () => {
  const valid = await step2Ref.value?.validate().catch(() => false)
  if (!valid) return

  resetLoading.value = true
  try {
    await userApi.resetPassword({
      phone: form.phone,
      code: form.code,
      newPassword: form.newPassword
    })
    ElMessage.success('密码重置成功！')
    step.value = 2
  } catch {
    // 验证码错误时退回到第一步重新输入
    step.value = 0
    form.code = ''
    mockCode.value = ''
  } finally {
    resetLoading.value = false
  }
}
</script>

<style scoped>
.forgot-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #0f172a 100%);
  position: relative;
  overflow: hidden;
}

.bg-decoration { position: absolute; inset: 0; pointer-events: none; }
.circle { position: absolute; border-radius: 50%; }
.circle-1 { width: 500px; height: 500px; top: -150px; right: -100px; background: radial-gradient(circle, rgba(37,99,235,0.1), transparent); }
.circle-2 { width: 350px; height: 350px; bottom: -100px; left: -80px; background: radial-gradient(circle, rgba(124,58,237,0.1), transparent); }

.forgot-card {
  width: 480px;
  max-width: calc(100vw - 48px);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 40px;
  backdrop-filter: blur(20px);
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.5);
}

.card-header { text-align: center; margin-bottom: 28px; }

.logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.logo-icon {
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 22px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 1px;
}

.card-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #f1f5f9;
  margin-bottom: 6px;
}

.card-header p { color: #64748b; font-size: 14px; }

.steps {
  margin-bottom: 32px;
}

:deep(.el-step__title) {
  font-size: 12px !important;
  color: #64748b !important;
}

:deep(.el-step__title.is-process) { color: #60a5fa !important; }
:deep(.el-step__title.is-finish) { color: #10b981 !important; }
:deep(.el-step__head.is-finish .el-step__line) { background-color: #10b981 !important; }

:deep(.el-form-item__label) { color: #94a3b8 !important; font-size: 13px; }
:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  box-shadow: none !important;
}
:deep(.el-input__inner) { color: #e2e8f0 !important; }
:deep(.el-input__inner::placeholder) { color: #4b5563 !important; }

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-row .el-input { flex: 1; }

.send-btn {
  flex-shrink: 0;
  height: 40px;
  padding: 0 16px;
  background: rgba(37, 99, 235, 0.2) !important;
  border: 1px solid rgba(37, 99, 235, 0.4) !important;
  color: #60a5fa !important;
  border-radius: 8px !important;
  font-size: 13px;
  white-space: nowrap;
  transition: all 0.2s;
}

.send-btn:not(:disabled):hover {
  background: rgba(37, 99, 235, 0.35) !important;
  border-color: rgba(37, 99, 235, 0.6) !important;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.mock-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.25);
  border-radius: 8px;
  color: #fbbf24;
  font-size: 13px;
  margin-bottom: 16px;
}

.action-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #2563eb, #7c3aed) !important;
  border: none !important;
  border-radius: 10px !important;
  margin-top: 8px;
  letter-spacing: 1px;
}

.success-state {
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  margin-bottom: 16px;
}

.success-state h3 {
  font-size: 20px;
  font-weight: 700;
  color: #f1f5f9;
  margin-bottom: 8px;
}

.success-state p {
  color: #64748b;
  font-size: 14px;
  margin-bottom: 28px;
}

.card-footer {
  text-align: center;
  margin-top: 20px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #60a5fa;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: color 0.2s;
}

.back-link:hover { color: #93c5fd; }
</style>
