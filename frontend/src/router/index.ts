import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 登录页
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    // 注册页
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/auth/RegisterView.vue'),
      meta: { requiresAuth: false }
    },
    // 找回密码
    {
      path: '/forgot-password',
      name: 'ForgotPassword',
      component: () => import('@/views/auth/ForgotPasswordView.vue'),
      meta: { requiresAuth: false }
    },
    // 主布局（需要认证）
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/chat'
        },
        // 智能问答
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('@/views/chat/ChatView.vue'),
          meta: { title: '智能问答', icon: 'ChatDotRound' }
        },
        {
          path: 'chat/:id',
          name: 'ChatDetail',
          component: () => import('@/views/chat/ChatView.vue'),
          meta: { title: '智能问答', icon: 'ChatDotRound' }
        },
        // 知识库管理
        {
          path: 'knowledge',
          name: 'Knowledge',
          component: () => import('@/views/knowledge/KnowledgeView.vue'),
          meta: { title: '知识库', icon: 'Reading', requiresAdmin: true }
        },
        // 数据统计
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '数据大屏', icon: 'DataAnalysis', requiresAdmin: true }
        },
        // 用户管理
        {
          path: 'users',
          name: 'Users',
          component: () => import('@/views/user/UserManageView.vue'),
          meta: { title: '用户管理', icon: 'User', requiresAdmin: true }
        },
        // AI 配置中心
        {
          path: 'ai-config',
          name: 'AiConfig',
          component: () => import('@/views/admin/AiConfigView.vue'),
          meta: { title: 'AI配置中心', icon: 'SetUp', requiresAdmin: true }
        },
        // 个人中心
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/user/ProfileView.vue'),
          meta: { title: '个人中心', icon: 'Setting' }
        }
      ]
    },
    // 404
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

// 全局路由守卫
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth === false) {
    // 已登录访问登录页，跳转首页
    if (userStore.isLoggedIn && (to.path === '/login' || to.path === '/register')) {
      return next('/')
    }
    return next()
  }

  // 未登录跳转登录页
  if (!userStore.isLoggedIn) {
    return next(`/login?redirect=${to.path}`)
  }

  // 加载用户信息
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      userStore.logout()
      return next('/login')
    }
  }

  next()
})

export default router
