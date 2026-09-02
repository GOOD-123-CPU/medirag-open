<template>
  <div class="user-manage-page">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="8">
          <el-input
            v-model="keyword"
            placeholder="搜索用户名、昵称、手机号"
            :prefix-icon="Search"
            clearable
            @change="loadUsers"
          />
        </el-col>
        <el-col :span="4">
          <el-select v-model="roleFilter" placeholder="全部角色" clearable @change="loadUsers">
            <el-option label="管理员" value="admin" />
            <el-option label="普通用户" value="user" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" :icon="Refresh" @click="loadUsers">刷新</el-button>
        </el-col>
        <el-col :span="8" style="text-align:right">
          <el-statistic title="用户总数" :value="total" />
        </el-col>
      </el-row>
    </el-card>

    <!-- 用户列表 -->
    <el-card class="table-card">
      <el-table
        :data="users"
        v-loading="loading"
        stripe
        row-key="id"
        style="width: 100%"
      >
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="36" :src="row.avatar">
                {{ row.nickname?.charAt(0) }}
              </el-avatar>
              <div class="user-cell-info">
                <div class="cell-name">{{ row.nickname || row.username }}</div>
                <div class="cell-username">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="phone" label="手机号" width="140">
          <template #default="{ row }">
            {{ row.phone || '-' }}
          </template>
        </el-table-column>

        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="getRoleType(row.role)">{{ getRoleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="(val: boolean) => toggleStatus(row, val)"
            />
          </template>
        </el-table-column>

        <el-table-column prop="lastLogin" label="最后登录" width="160">
          <template #default="{ row }">
            {{ row.lastLogin ? formatDate(row.lastLogin) : '未登录' }}
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="注册时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              text
              @click="openRoleDialog(row)"
            >调整角色</el-button>
            <el-button
              size="small"
              type="danger"
              text
              @click="disableUser(row)"
              v-if="row.status === 1 && row.role !== 'admin'"
            >禁用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="loadUsers"
        />
      </div>
    </el-card>

    <!-- 调整角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="调整用户角色" width="400px">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="用户">
          <span>{{ roleForm.username }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="roleForm.role" style="width:100%">
            <el-option label="管理员" value="admin" />
            <el-option label="普通用户" value="user" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { userApi, type UserInfo } from '@/api/user'

const loading = ref(false)
const users = ref<UserInfo[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const roleFilter = ref('')

const roleDialogVisible = ref(false)
const roleForm = reactive({ id: 0, username: '', role: '' })

const loadUsers = async () => {
  loading.value = true
  try {
    const result = await userApi.listUsers({
      current: current.value,
      size: pageSize.value,
      keyword: keyword.value || undefined
    })
    users.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)

const getRoleType = (role: string) => {
  return role === 'admin' ? 'danger' : ''
}

const getRoleLabel = (role: string) => {
  const map: Record<string, string> = { admin: '管理员', user: '用户' }
  return map[role] || role
}

const formatDate = (dateStr: string) => {
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

const toggleStatus = async (row: UserInfo, val: boolean) => {
  try {
    await userApi.updateUserStatus(row.id, val ? 1 : 0)
    row.status = val ? 1 : 0
    ElMessage.success(`已${val ? '启用' : '禁用'}用户 ${row.username}`)
  } catch {}
}

const disableUser = async (row: UserInfo) => {
  await ElMessageBox.confirm(`确认禁用用户 ${row.username}？`, '警告', { type: 'warning' })
  await toggleStatus(row, false)
}

const openRoleDialog = (row: UserInfo) => {
  roleForm.id = row.id
  roleForm.username = row.username
  roleForm.role = row.role
  roleDialogVisible.value = true
}

const saveRole = async () => {
  try {
    await userApi.updateUserRole(roleForm.id, roleForm.role)
    ElMessage.success('角色更新成功')
    roleDialogVisible.value = false
    loadUsers()
  } catch {}
}
</script>

<style scoped>
.user-manage-page { display: flex; flex-direction: column; gap: 16px; }
.search-card, .table-card { border-radius: 12px; }

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cell-name { font-weight: 600; font-size: 14px; color: #1e293b; }
.cell-username { font-size: 12px; color: #94a3b8; }

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
