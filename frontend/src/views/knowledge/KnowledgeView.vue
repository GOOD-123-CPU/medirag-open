<template>
  <div class="knowledge-page">
    <!-- 顶部统计卡 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <div class="stat-card" :style="{ borderTop: `3px solid ${stat.color}` }">
          <div class="stat-icon" :style="{ background: stat.color + '20', color: stat.color }">
            <el-icon size="22"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 操作栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="12" align="middle">
        <el-col :span="5">
          <el-select v-model="filterCategory" placeholder="全部分类" clearable @change="loadList">
            <el-option v-for="cat in categoryOptions" :key="cat.value" :label="cat.label" :value="cat.value" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filterStatus" placeholder="全部状态" clearable @change="loadList">
            <el-option label="处理中" value="processing" />
            <el-option label="就绪" value="ready" />
            <el-option label="失败" value="failed" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </el-col>
        <el-col :span="12" style="text-align:right">
          <el-button type="primary" :icon="Upload" @click="uploadDialogVisible = true">
            上传文档
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="examples-card">
      <template #header>
        <div class="examples-head">
          <span>专业知识库示例问题</span>
          <el-tag type="success" effect="plain" size="small">覆盖新增专科</el-tag>
        </div>
      </template>
      <div class="examples-grid">
        <div v-for="section in specialtyPromptSections" :key="section.title" class="example-group">
          <div class="example-group-title">{{ section.title }}</div>
          <div class="example-tags">
            <el-tag
              v-for="prompt in section.prompts"
              :key="prompt"
              effect="plain"
              class="example-tag"
            >
              {{ prompt }}
            </el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 知识库列表 -->
    <el-card class="list-card">
      <el-table
        :data="knowledgeList"
        v-loading="loading"
        stripe
        row-key="id"
      >
        <!-- 文档名称 -->
        <el-table-column label="文档信息" min-width="280">
          <template #default="{ row }">
            <div class="doc-cell">
              <div class="doc-icon" :class="row.fileType">
                <el-icon><component :is="getFileIcon(row.fileType)" /></el-icon>
              </div>
              <div class="doc-info">
                <div class="doc-name">{{ row.name }}</div>
                <div class="doc-meta">
                  <span>{{ formatFileSize(row.fileSize) }}</span>
                  <el-divider direction="vertical" />
                  <span>{{ row.fileType?.toUpperCase() }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <!-- 分类 -->
        <el-table-column label="科室分类" width="120">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ getCategoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>

        <!-- 切片数 -->
        <el-table-column label="切片数量" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="info" effect="plain">{{ row.chunkCount }}</el-tag>
          </template>
        </el-table-column>

        <!-- 状态 -->
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" effect="light">
              <el-icon v-if="row.status === 'processing'" class="is-loading"><Loading /></el-icon>
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 上传时间 -->
        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>

        <!-- 操作 -->
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="viewDetail(row)">
              <el-icon><View /></el-icon> 详情
            </el-button>
            <el-button
              v-if="row.status === 'failed'"
              size="small" text type="warning"
              @click="reprocess(row.id)"
            >
              重试
            </el-button>
            <el-button size="small" text type="danger" @click="deleteDoc(row.id)">
              删除
            </el-button>
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
          @change="loadList"
        />
      </div>
    </el-card>

    <!-- ========================
         上传文档弹窗
    ========================= -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传医学文档"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="uploadFormRef"
        :model="uploadForm"
        :rules="uploadRules"
        label-width="80px"
        size="large"
      >
        <!-- 文件选择区 -->
        <el-form-item label="文件" prop="file">
          <div
            class="upload-zone"
            :class="{ 'drag-over': isDragOver }"
            @click="fileInputRef?.click()"
            @dragover.prevent="isDragOver = true"
            @dragleave="isDragOver = false"
            @drop.prevent="handleFileDrop"
          >
            <div v-if="!selectedFile" class="upload-placeholder">
              <el-icon size="40" color="#94a3b8"><UploadFilled /></el-icon>
              <p>点击或拖拽文件到此区域上传</p>
              <p class="upload-hint">支持 PDF、Word(.docx)、TXT 格式，最大 100MB</p>
            </div>
            <div v-else class="selected-file">
              <el-icon size="36" :color="getFileColor(selectedFile.name)">
                <component :is="getFileIcon(getFileExtension(selectedFile.name))" />
              </el-icon>
              <div class="file-info">
                <div class="file-name">{{ selectedFile.name }}</div>
                <div class="file-size">{{ formatFileSize(selectedFile.size) }}</div>
              </div>
              <el-button text type="danger" @click.stop="selectedFile = null">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </div>
          <input
            ref="fileInputRef"
            type="file"
            accept=".pdf,.docx,.doc,.txt"
            style="display:none"
            @change="handleFileSelect"
          />
        </el-form-item>

        <!-- 科室分类 -->
        <el-form-item label="科室分类" prop="category">
          <el-select v-model="uploadForm.category" placeholder="请选择科室分类" style="width:100%">
            <el-option
              v-for="cat in categoryOptions"
              :key="cat.value"
              :label="cat.label"
              :value="cat.value"
            />
          </el-select>
        </el-form-item>

        <!-- 描述 -->
        <el-form-item label="文档描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            placeholder="简要描述文档内容（可选）"
          />
        </el-form-item>

        <!-- 进度条 -->
        <el-form-item v-if="uploading">
          <div class="upload-progress">
            <div class="progress-label">
              <span>{{ uploadProgress < 100 ? '上传中...' : '处理中...' }}</span>
              <span>{{ uploadProgress }}%</span>
            </div>
            <el-progress :percentage="uploadProgress" :striped="uploadProgress < 100" :striped-flow="uploadProgress < 100" />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="closeUploadDialog">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">
          {{ uploading ? '处理中...' : '立即上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ========================
         文档详情弹窗
    ========================= -->
    <el-dialog v-model="detailDialogVisible" title="文档详情" width="560px">
      <el-descriptions :column="2" border v-if="currentDoc">
        <el-descriptions-item label="文档名称" :span="2">{{ currentDoc.name }}</el-descriptions-item>
        <el-descriptions-item label="文件类型">{{ currentDoc.fileType?.toUpperCase() }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatFileSize(currentDoc.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="科室分类">{{ getCategoryLabel(currentDoc.category) }}</el-descriptions-item>
        <el-descriptions-item label="切片数量">{{ currentDoc.chunkCount }}</el-descriptions-item>
        <el-descriptions-item label="处理状态">
          <el-tag :type="getStatusType(currentDoc.status)">{{ getStatusLabel(currentDoc.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="上传时间">{{ formatDate(currentDoc.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="文档描述" :span="2">{{ currentDoc.description || '无' }}</el-descriptions-item>
        <el-descriptions-item v-if="currentDoc.errorMsg" label="错误信息" :span="2">
          <el-text type="danger">{{ currentDoc.errorMsg }}</el-text>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Upload, Refresh } from '@element-plus/icons-vue'
import { knowledgeApi, type KnowledgeBase } from '@/api/knowledge'
import { specialtyPromptSections } from '@/constants/examplePrompts'

// ===================== 数据 =====================
const loading = ref(false)
const knowledgeList = ref<KnowledgeBase[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)
const filterCategory = ref('')
const filterStatus = ref('')

// ===================== 统计 =====================
const stats = computed(() => [
  { label: '文档总数', value: total.value, icon: 'Document', color: '#3b82f6' },
  { label: '已就绪', value: knowledgeList.value.filter(k => k.status === 'ready').length, icon: 'CircleCheck', color: '#10b981' },
  { label: '处理中', value: knowledgeList.value.filter(k => ['uploading', 'processing'].includes(k.status)).length, icon: 'Loading', color: '#f59e0b' },
  { label: '知识切片', value: knowledgeList.value.reduce((s, k) => s + (k.chunkCount || 0), 0), icon: 'Grid', color: '#7c3aed' }
])

// ===================== 分类 =====================
const categoryOptions = [
  { label: '内科', value: 'neike' },
  { label: '外科', value: 'waike' },
  { label: '儿科', value: 'erke' },
  { label: '妇科', value: 'fuke' },
  { label: '神经科', value: 'shenjingke' },
  { label: '骨科', value: 'guke' },
  { label: '皮肤科', value: 'pifuke' },
  { label: '眼科', value: 'yanke' },
  { label: '药学', value: 'yaoxue' },
  { label: '急诊科', value: 'jizhengke' },
  { label: '综合', value: 'general' }
]

const getCategoryLabel = (value: string) =>
  categoryOptions.find(c => c.value === value)?.label || value

// ===================== 上传弹窗 =====================
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const selectedFile = ref<File | null>(null)
const fileInputRef = ref<HTMLInputElement>()
const isDragOver = ref(false)
const uploadFormRef = ref<FormInstance>()

const uploadForm = reactive({ category: '', description: '' })

const uploadRules: FormRules = {
  category: [{ required: true, message: '请选择科室分类', trigger: 'change' }]
}

const handleFileSelect = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (input.files?.[0]) {
    selectedFile.value = input.files[0]
  }
}

const handleFileDrop = (e: DragEvent) => {
  isDragOver.value = false
  const file = e.dataTransfer?.files[0]
  if (file) selectedFile.value = file
}

const submitUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  const valid = await uploadFormRef.value?.validate().catch(() => false)
  if (!valid) return

  uploading.value = true
  uploadProgress.value = 0
  try {
    await knowledgeApi.upload(
      selectedFile.value,
      uploadForm.category,
      uploadForm.description,
      (p) => { uploadProgress.value = p }
    )
    uploadProgress.value = 100
    ElMessage.success('文档上传成功，正在后台处理...')
    closeUploadDialog()
    loadList()
  } catch {
    // 错误已处理
  } finally {
    uploading.value = false
  }
}

const closeUploadDialog = () => {
  uploadDialogVisible.value = false
  selectedFile.value = null
  uploadProgress.value = 0
  uploadForm.category = ''
  uploadForm.description = ''
}

// ===================== 详情弹窗 =====================
const detailDialogVisible = ref(false)
const currentDoc = ref<KnowledgeBase | null>(null)

const viewDetail = (row: KnowledgeBase) => {
  currentDoc.value = row
  detailDialogVisible.value = true
}

// ===================== 操作 =====================
const loadList = async () => {
  loading.value = true
  try {
    const result = await knowledgeApi.list({
      current: current.value,
      size: pageSize.value,
      category: filterCategory.value || undefined,
      status: filterStatus.value || undefined
    })
    knowledgeList.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

const deleteDoc = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除该文档？将同步删除向量数据，此操作不可恢复。', '警告', {
      type: 'warning',
      confirmButtonText: '确认删除',
      confirmButtonType: 'danger'
    })
  } catch {
    return // 用户点了取消
  }
  try {
    await knowledgeApi.delete(id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 错误消息已由 axios 拦截器处理
  }
}

const reprocess = async (id: number) => {
  try {
    await knowledgeApi.reprocess(id)
    ElMessage.success('已重新提交处理')
    loadList()
  } catch {
    // 错误消息已由 axios 拦截器处理
  }
}

// ===================== 自动轮询（处理中状态自动刷新） =====================
let pollTimer: ReturnType<typeof setInterval> | null = null

const hasProcessingDocs = computed(() =>
  knowledgeList.value.some(k => ['uploading', 'processing'].includes(k.status))
)

// 有处理中的文档时启动轮询，全部完成后停止
watch(hasProcessingDocs, (hasProcessing) => {
  if (hasProcessing) {
    if (!pollTimer) {
      pollTimer = setInterval(loadList, 3000)
    }
  } else {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

onMounted(loadList)

// ===================== 工具函数 =====================
const getFileIcon = (type: string) => {
  const map: Record<string, string> = { pdf: 'Document', docx: 'Memo', doc: 'Memo', txt: 'DocumentCopy' }
  return map[type?.toLowerCase()] || 'Document'
}

const getFileColor = (name: string) => {
  const ext = name.split('.').pop()?.toLowerCase()
  const map: Record<string, string> = { pdf: '#ef4444', docx: '#3b82f6', doc: '#3b82f6', txt: '#6b7280' }
  return map[ext || ''] || '#6b7280'
}

const getFileExtension = (name: string) => name.split('.').pop()?.toLowerCase() || ''

const getStatusType = (status: string) => {
  const map: Record<string, string> = { uploading: 'warning', processing: 'warning', ready: 'success', failed: 'danger' }
  return map[status] as any || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = { uploading: '上传中', processing: '处理中', ready: '就绪', failed: '失败' }
  return map[status] || status
}

const formatFileSize = (bytes: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}
</script>

<style scoped>
.knowledge-page { display: flex; flex-direction: column; gap: 16px; }

/* 统计卡 */
.stat-row { margin-bottom: 4px; }
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: var(--card-shadow);
  transition: var(--transition);
}
.stat-card:hover { box-shadow: var(--card-shadow-hover); transform: translateY(-2px); }
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value { font-size: 24px; font-weight: 700; color: #1e293b; }
.stat-label { font-size: 12px; color: #94a3b8; margin-top: 2px; }

/* 操作栏 */
.toolbar-card { border-radius: 12px; }
.examples-card { border-radius: 12px; }
.list-card { border-radius: 12px; }

.examples-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: #1e293b;
}

.examples-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.example-group {
  border: 1px solid #eef2ff;
  border-radius: 12px;
  padding: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.example-group-title {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 10px;
}

.example-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.example-tag {
  white-space: normal;
  height: auto;
  line-height: 1.45;
  padding: 6px 10px;
}

/* 文档单元格 */
.doc-cell { display: flex; align-items: center; gap: 12px; }
.doc-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.doc-icon.pdf { background: rgba(239,68,68,0.1); color: #ef4444; }
.doc-icon.docx, .doc-icon.doc { background: rgba(59,130,246,0.1); color: #3b82f6; }
.doc-icon.txt { background: rgba(107,114,128,0.1); color: #6b7280; }
.doc-name { font-weight: 600; font-size: 14px; color: #1e293b; }
.doc-meta { font-size: 12px; color: #94a3b8; margin-top: 2px; }

.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }

/* 上传区域 */
.upload-zone {
  width: 100%;
  border: 2px dashed #e2e8f0;
  border-radius: 12px;
  padding: 32px 20px;
  text-align: center;
  cursor: pointer;
  transition: var(--transition);
}
.upload-zone:hover, .upload-zone.drag-over {
  border-color: #3b82f6;
  background: rgba(59,130,246,0.04);
}
.upload-placeholder p { color: #94a3b8; margin-top: 8px; }
.upload-hint { font-size: 12px !important; color: #cbd5e1 !important; }

.selected-file {
  display: flex;
  align-items: center;
  gap: 12px;
}
.file-name { font-weight: 600; color: #1e293b; }
.file-size { font-size: 12px; color: #94a3b8; margin-top: 2px; }
.file-info { flex: 1; text-align: left; }

.upload-progress { width: 100%; }
.progress-label { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 13px; color: #64748b; }

@media (max-width: 1200px) {
  .examples-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 768px) {
  .examples-grid { grid-template-columns: 1fr; }
}
</style>
