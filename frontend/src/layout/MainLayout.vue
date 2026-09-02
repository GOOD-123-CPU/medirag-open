<template>
  <div class="main-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <!-- Logo -->
      <div class="sidebar-logo">
        <div class="logo-icon">
          <MediRagIcon :size="26" />
        </div>
        <span class="logo-text" v-show="!isCollapsed">MediRAG</span>
      </div>

      <!-- 导航菜单 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          v-show="canAccess(item)"
        >
          <el-icon size="20"><component :is="item.icon" /></el-icon>
          <span class="nav-label" v-show="!isCollapsed">{{ item.label }}</span>
        </router-link>
      </nav>

      <!-- 用户信息 -->
      <div class="sidebar-user" v-show="!isCollapsed">
        <el-avatar :size="36" :src="userStore.userInfo?.avatar">
          {{ userStore.userInfo?.nickname?.charAt(0) }}
        </el-avatar>
        <div class="user-info">
          <div class="user-name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</div>
          <div class="user-role">
            <el-tag size="small" :type="roleTagType">{{ roleLabel }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 折叠按钮 -->
      <div class="collapse-btn" @click="isCollapsed = !isCollapsed">
        <el-icon><component :is="isCollapsed ? 'Expand' : 'Fold'" /></el-icon>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 顶部栏 -->
      <header class="top-header">
        <div class="header-left">
          <div class="page-title">{{ currentTitle }}</div>
          <div class="breadcrumb">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
        </div>
        <div class="header-right">
          <el-tooltip content="GitHub">
            <el-button text circle>
              <el-icon><Link /></el-icon>
            </el-button>
          </el-tooltip>
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="header-user">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar">
                {{ userStore.userInfo?.nickname?.charAt(0) }}
              </el-avatar>
              <span class="header-username">{{ userStore.userInfo?.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon> 个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 页面内容 -->
      <div class="page-body">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import MediRagIcon from '@/components/MediRagIcon.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapsed = ref(false)

const menuItems = [
  { path: '/chat', label: '智能问答', icon: 'ChatDotRound' },
  { path: '/knowledge', label: '知识库管理', icon: 'Reading', requiresAdmin: true },
  { path: '/dashboard', label: '数据大屏', icon: 'DataAnalysis', requiresAdmin: true },
  { path: '/users', label: '用户管理', icon: 'UserFilled', requiresAdmin: true },
  { path: '/ai-config', label: 'AI配置中心', icon: 'SetUp', requiresAdmin: true },
  { path: '/profile', label: '个人中心', icon: 'Setting' }
]

const canAccess = (item: any) => {
  if (item.requiresAdmin) return userStore.isAdmin
  return true
}

const isActive = (path: string) => route.path.startsWith(path)

const currentTitle = computed(() => {
  const item = menuItems.find(m => route.path.startsWith(m.path))
  return item?.label || 'MediRAG'
})

const roleLabel = computed(() => {
  const map: Record<string, string> = { admin: '管理员', user: '普通用户' }
  return map[userStore.userInfo?.role || 'user'] || '用户'
})

const roleTagType = computed(() => {
  return userStore.isAdmin ? 'danger' : ''
})

const handleCommand = async (command: string) => {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ---- 侧边栏 ---- */
.sidebar {
  width: var(--sidebar-width);
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease;
  position: relative;
  flex-shrink: 0;
  box-shadow: 4px 0 20px rgba(0, 0, 0, 0.3);
}

.sidebar.collapsed {
  width: 64px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  color: #94a3b8;
  text-decoration: none;
  transition: var(--transition);
  white-space: nowrap;
  overflow: hidden;
}

.nav-item:hover {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
}

.nav-item.active {
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.3), rgba(124, 58, 237, 0.3));
  color: #93c5fd;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.2);
}

.nav-label {
  font-size: 14px;
  font-weight: 500;
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  truncate: true;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  margin-top: 2px;
}

.collapse-btn {
  position: absolute;
  bottom: 80px;
  right: -12px;
  width: 24px;
  height: 24px;
  background: #1e293b;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #64748b;
  transition: var(--transition);
  z-index: 10;
}

.collapse-btn:hover {
  color: #3b82f6;
  border-color: #3b82f6;
}

/* ---- 主内容 ---- */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-color);
}

.top-header {
  height: var(--header-height);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
  z-index: 5;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: var(--transition);
}

.header-user:hover {
  background: #f1f5f9;
}

.header-username {
  font-size: 14px;
  color: #475569;
  font-weight: 500;
}

.page-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
</style>
