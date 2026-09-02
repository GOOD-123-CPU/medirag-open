<template>
  <div class="profile-page">
    <el-row :gutter="24">
      <!-- 左侧个人信息卡 -->
      <el-col :span="8">
        <el-card class="profile-card">
          <div class="avatar-section">
            <div class="avatar-wrapper" @click="triggerAvatarUpload">
              <el-avatar :size="100" :src="avatarPreview || userInfo?.avatar" class="user-avatar">
                <span class="avatar-fallback">{{ userInfo?.nickname?.charAt(0) }}</span>
              </el-avatar>
              <div class="avatar-upload-btn" :class="{ uploading: avatarUploading }">
                <el-icon v-if="!avatarUploading"><Camera /></el-icon>
                <el-icon v-else class="is-loading"><Loading /></el-icon>
              </div>
              <input
                ref="avatarInputRef"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                style="display:none"
                @change="onAvatarFileChange"
              />
            </div>
            <h3 class="user-name">{{ userInfo?.nickname }}</h3>
            <div class="user-meta">
              <el-tag :type="roleTagType">{{ roleLabel }}</el-tag>
            </div>
          </div>

          <el-divider />

          <div class="info-list">
            <div class="info-item">
              <el-icon class="info-icon"><User /></el-icon>
              <div>
                <div class="info-label">用户名</div>
                <div class="info-value">{{ userInfo?.username }}</div>
              </div>
            </div>
            <div class="info-item">
              <el-icon class="info-icon"><Phone /></el-icon>
              <div>
                <div class="info-label">手机号</div>
                <div class="info-value">{{ userInfo?.phone || '未绑定' }}</div>
              </div>
            </div>
            <div class="info-item">
              <el-icon class="info-icon"><Calendar /></el-icon>
              <div>
                <div class="info-label">注册时间</div>
                <div class="info-value">{{ formatDate(userInfo?.createTime) }}</div>
              </div>
            </div>
            <div class="info-item">
              <el-icon class="info-icon"><Clock /></el-icon>
              <div>
                <div class="info-label">最后登录</div>
                <div class="info-value">{{ formatDate(userInfo?.lastLogin) }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧编辑区 -->
      <el-col :span="16">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <!-- 基本信息 -->
          <el-tab-pane label="基本信息" name="basic">
            <el-card>
              <el-form
                ref="basicFormRef"
                :model="basicForm"
                label-width="80px"
                size="large"
              >
                <el-form-item label="昵称">
                  <el-input v-model="basicForm.nickname" placeholder="请输入昵称" />
                </el-form-item>
                <el-form-item label="手机号">
                  <el-input v-model="basicForm.phone" placeholder="请输入手机号" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="basicLoading" @click="saveBasicInfo">
                    保存修改
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- 健康档案 -->
          <el-tab-pane label="健康档案" name="health">
            <el-card>
              <div class="health-tip">
                <el-icon color="#3b82f6"><InfoFilled /></el-icon>
                <span>健康档案信息将在问答时自动注入，帮助获得更个性化的医疗建议</span>
              </div>

              <el-form
                :model="healthForm"
                label-width="100px"
                size="large"
                style="margin-top: 20px"
              >
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="年龄">
                      <el-input-number v-model="healthForm.age" :min="1" :max="150" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="性别">
                      <el-select v-model="healthForm.gender" style="width:100%">
                        <el-option label="男" value="male" />
                        <el-option label="女" value="female" />
                        <el-option label="不填写" value="" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-form-item label="过敏史">
                  <el-input
                    v-model="healthForm.allergies"
                    type="textarea"
                    :rows="3"
                    placeholder="如：青霉素过敏、花粉过敏等（逗号分隔）"
                  />
                </el-form-item>

                <el-form-item label="慢性病史">
                  <el-input
                    v-model="healthForm.conditions"
                    type="textarea"
                    :rows="3"
                    placeholder="如：高血压、糖尿病等（逗号分隔）"
                  />
                </el-form-item>

                <el-form-item label="用药史">
                  <el-input
                    v-model="healthForm.medications"
                    type="textarea"
                    :rows="3"
                    placeholder="目前正在服用的药物"
                  />
                </el-form-item>

                <el-form-item>
                  <el-button type="primary" :loading="healthLoading" @click="saveHealthProfile">
                    保存健康档案
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- 安全设置 -->
          <el-tab-pane label="安全设置" name="security">
            <el-card>
              <el-form label-width="100px" size="large">
                <el-form-item label="当前密码">
                  <el-input type="password" placeholder="请输入当前密码" show-password />
                </el-form-item>
                <el-form-item label="新密码">
                  <el-input type="password" placeholder="请输入新密码" show-password />
                </el-form-item>
                <el-form-item label="确认新密码">
                  <el-input type="password" placeholder="请再次输入新密码" show-password />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary">修改密码</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api/user'
import { Loading } from '@element-plus/icons-vue'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
const activeTab = ref('basic')
const basicLoading = ref(false)
const healthLoading = ref(false)
const avatarUploading = ref(false)
const avatarPreview = ref<string>('')
const avatarInputRef = ref<HTMLInputElement>()

const triggerAvatarUpload = () => {
  avatarInputRef.value?.click()
}

const onAvatarFileChange = async (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return

  // 本地预览
  avatarPreview.value = URL.createObjectURL(file)

  avatarUploading.value = true
  try {
    await userApi.uploadAvatar(file)
    await userStore.fetchUserInfo()
    ElMessage.success('头像更新成功')
  } catch {
    avatarPreview.value = ''
    ElMessage.error('头像上传失败，请重试')
  } finally {
    avatarUploading.value = false
    // 清空 input，允许重复选择同一文件
    if (avatarInputRef.value) avatarInputRef.value.value = ''
  }
}

const basicForm = reactive({
  nickname: '',
  phone: ''
})

const healthForm = reactive({
  age: undefined as number | undefined,
  gender: '',
  allergies: '',
  conditions: '',
  medications: ''
})

const roleLabel = computed(() => {
  const map: Record<string, string> = { admin: '管理员', user: '普通用户' }
  return map[userInfo.value?.role || 'user'] || '用户'
})

const roleTagType = computed(() => {
  return userInfo.value?.role === 'admin' ? 'danger' : ''
})

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '未知'
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit'
  })
}

onMounted(() => {
  if (userInfo.value) {
    basicForm.nickname = userInfo.value.nickname || ''
    basicForm.phone = userInfo.value.phone || ''
    if (userInfo.value.healthProfile) {
      try {
        const profile = JSON.parse(userInfo.value.healthProfile)
        Object.assign(healthForm, profile)
      } catch {}
    }
  }
})

const saveBasicInfo = async () => {
  basicLoading.value = true
  try {
    await userApi.updateUserInfo(basicForm)
    await userStore.fetchUserInfo()
    ElMessage.success('信息更新成功')
  } finally {
    basicLoading.value = false
  }
}

const saveHealthProfile = async () => {
  healthLoading.value = true
  try {
    await userApi.updateHealthProfile(JSON.stringify(healthForm))
    await userStore.fetchUserInfo()
    ElMessage.success('健康档案已保存')
  } finally {
    healthLoading.value = false
  }
}
</script>

<style scoped>
.profile-page { max-width: 1100px; margin: 0 auto; }

.profile-card { height: fit-content; }

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.avatar-wrapper {
  position: relative;
  margin-bottom: 16px;
  cursor: pointer;
  user-select: none;
}

.avatar-wrapper:hover .avatar-upload-btn {
  opacity: 1;
}

.avatar-fallback {
  font-size: 36px;
  font-weight: 700;
  color: #fff;
}

.user-avatar {
  background: linear-gradient(135deg, #2563eb, #7c3aed);
}

.avatar-upload-btn {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 30px;
  height: 30px;
  background: #2563eb;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.2);
  opacity: 0.85;
  transition: opacity 0.2s, background 0.2s;
}

.avatar-upload-btn.uploading {
  background: #64748b;
  opacity: 1;
}

.avatar-upload-btn .is-loading {
  animation: rotating 1s linear infinite;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.user-name {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 8px;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.info-icon {
  color: #3b82f6;
  margin-top: 2px;
  flex-shrink: 0;
}

.info-label { font-size: 12px; color: #94a3b8; margin-bottom: 2px; }
.info-value { font-size: 14px; color: #1e293b; font-weight: 500; }

.profile-tabs :deep(.el-tabs__header) { margin-bottom: 0; }

.health-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(59, 130, 246, 0.06);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  color: #475569;
  font-size: 13px;
}
</style>
