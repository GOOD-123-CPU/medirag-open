<template>
  <div class="dashboard-page">
    <!-- 顶部控制栏 -->
    <div class="dashboard-header">
      <div class="header-left">
        <h2 class="dashboard-title">
          <span class="gradient-text">MediRAG</span> 数据大屏
        </h2>
        <el-tag type="success" effect="plain" size="small">实时数据</el-tag>
      </div>
      <div class="header-right">
        <el-radio-group v-model="timeRange" size="small" @change="loadData">
          <el-radio-button value="today">今日</el-radio-button>
          <el-radio-button value="week">近7天</el-radio-button>
          <el-radio-button value="month">近30天</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" size="small" circle :loading="loading" @click="loadData" />
      </div>
    </div>

    <div v-loading="loading" class="dashboard-body">
      <!-- ===== 核心指标卡 ===== -->
      <el-row :gutter="16" class="metric-row">
        <el-col :span="6" v-for="m in metrics" :key="m.label">
          <div class="metric-card" :style="{ '--accent': m.color }">
            <div class="metric-icon" :style="{ background: m.color + '22', color: m.color }">
              <el-icon size="26"><component :is="m.icon" /></el-icon>
            </div>
            <div class="metric-body">
              <div class="metric-value">
                <count-up :end-val="m.value" :duration="1.5" />
              </div>
              <div class="metric-label">{{ m.label }}</div>
              <div class="metric-sub" v-if="m.sub">{{ m.sub }}</div>
            </div>
            <div class="metric-wave"></div>
          </div>
        </el-col>
      </el-row>

      <!-- ===== 第一行图表 ===== -->
      <el-row :gutter="16" class="chart-row">
        <!-- 问答趋势折线图 -->
        <el-col :span="16">
          <el-card class="chart-card">
            <template #header>
              <div class="card-head">
                <span>问答量趋势</span>
                <el-tag type="info" size="small">近14天</el-tag>
              </div>
            </template>
            <div ref="trendChartRef" class="chart-container"></div>
          </el-card>
        </el-col>

        <!-- 用户角色分布饼图 -->
        <el-col :span="8">
          <el-card class="chart-card">
            <template #header>
              <div class="card-head"><span>用户角色分布</span></div>
            </template>
            <div ref="roleChartRef" class="chart-container"></div>
          </el-card>
        </el-col>
      </el-row>

      <!-- ===== 第二行图表 ===== -->
      <el-row :gutter="16" class="chart-row">
        <!-- 知识库分类分布 -->
        <el-col :span="8">
          <el-card class="chart-card">
            <template #header>
              <div class="card-head"><span>知识库科室分布</span></div>
            </template>
            <div ref="categoryChartRef" class="chart-container"></div>
          </el-card>
        </el-col>

        <!-- 知识库状态统计柱状图 -->
        <el-col :span="8">
          <el-card class="chart-card">
            <template #header>
              <div class="card-head"><span>知识库状态分布</span></div>
            </template>
            <div ref="kbStatusChartRef" class="chart-container"></div>
          </el-card>
        </el-col>

        <!-- 检索质量 / 反馈统计 -->
        <el-col :span="8">
          <el-card class="chart-card">
            <template #header>
              <div class="card-head"><span>检索质量统计</span></div>
            </template>
            <div class="quality-stats">
              <!-- 满意度 -->
              <div class="quality-item">
                <div class="quality-label">用户满意度</div>
                <el-progress
                  :percentage="satisfactionPct"
                  :stroke-width="12"
                  color="#10b981"
                  :format="() => data?.feedbackStats?.satisfactionRate || 'N/A'"
                />
              </div>
              <!-- 兜底率 -->
              <div class="quality-item">
                <div class="quality-label">检索兜底率</div>
                <el-progress
                  :percentage="fallbackPct"
                  :stroke-width="12"
                  :color="fallbackPct > 30 ? '#ef4444' : '#f59e0b'"
                  :format="() => data?.fallbackStats?.fallbackRate || '0%'"
                />
              </div>
              <!-- 详细数字 -->
              <div class="quality-detail">
                <div class="qd-item">
                  <div class="qd-val positive">{{ data?.feedbackStats?.useful || 0 }}</div>
                  <div class="qd-label">有用反馈</div>
                </div>
                <div class="qd-item">
                  <div class="qd-val negative">{{ data?.feedbackStats?.useless || 0 }}</div>
                  <div class="qd-label">无用反馈</div>
                </div>
                <div class="qd-item">
                  <div class="qd-val warning">{{ data?.fallbackStats?.fallback || 0 }}</div>
                  <div class="qd-label">兜底次数</div>
                </div>
                <div class="qd-item">
                  <div class="qd-val">{{ data?.fallbackStats?.normal || 0 }}</div>
                  <div class="qd-label">正常检索</div>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- ===== 热门关键词词云 ===== -->
      <el-row :gutter="16" class="chart-row">
        <el-col :span="24">
          <el-card class="chart-card">
            <template #header>
              <div class="card-head">
                <span>热门医疗关键词 TOP 15</span>
                <el-tag size="small" type="warning">基于近100条问答</el-tag>
              </div>
            </template>
            <div class="word-cloud">
              <span
                v-for="(kw, i) in (data?.hotKeywords || [])"
                :key="kw.name"
                class="word-item"
                :style="getWordStyle(kw.value, i, kw.name)"
                :title="`${kw.name}：出现 ${kw.value} 次`"
              >{{ kw.name }}</span>
              <div v-if="!data?.hotKeywords?.length" class="empty-keywords">
                暂无数据，开始问答后将显示热门关键词
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { statsApi, type DashboardData } from '@/api/stats'

// ===================== 数据状态 =====================
const loading = ref(false)
const timeRange = ref('week')
const data = ref<DashboardData | null>(null)

// 图表 DOM 引用
const trendChartRef = ref<HTMLElement>()
const roleChartRef = ref<HTMLElement>()
const categoryChartRef = ref<HTMLElement>()
const kbStatusChartRef = ref<HTMLElement>()

// ECharts 实例
let trendChart: echarts.ECharts | null = null
let roleChart: echarts.ECharts | null = null
let categoryChart: echarts.ECharts | null = null
let kbStatusChart: echarts.ECharts | null = null

// ===================== 计算属性 =====================
const metrics = computed(() => [
  {
    label: '注册用户数',
    value: data.value?.totalUsers || 0,
    icon: 'UserFilled',
    color: '#3b82f6',
    sub: '累计'
  },
  {
    label: '问答总量',
    value: data.value?.totalMessages || 0,
    icon: 'ChatLineRound',
    color: '#7c3aed',
    sub: `本期 +${data.value?.periodMessages || 0}`
  },
  {
    label: '知识文档',
    value: data.value?.totalKnowledge || 0,
    icon: 'Reading',
    color: '#10b981',
    sub: '已就绪'
  },
  {
    label: '对话会话',
    value: data.value?.totalConversations || 0,
    icon: 'ChatDotRound',
    color: '#f59e0b',
    sub: '累计'
  }
])

const satisfactionPct = computed(() => {
  const stats = data.value?.feedbackStats
  if (!stats) return 0
  const total = stats.useful + stats.useless
  return total > 0 ? Math.round(stats.useful * 100 / total) : 0
})

const fallbackPct = computed(() => {
  const stats = data.value?.fallbackStats
  if (!stats || stats.total === 0) return 0
  return Math.round(stats.fallback * 100 / stats.total)
})

// ===================== 数据加载 =====================
const loadData = async () => {
  loading.value = true
  try {
    data.value = await statsApi.getDashboard(timeRange.value)
    await nextTick()
    renderCharts()
  } catch (e) {
    // 管理员才能访问，非管理员不报错
  } finally {
    loading.value = false
  }
}

// ===================== 图表渲染 =====================
const CHART_COLORS = ['#3b82f6', '#7c3aed', '#10b981', '#f59e0b', '#ef4444', '#06b6d4', '#8b5cf6', '#ec4899']

const renderCharts = () => {
  renderTrendChart()
  renderRoleChart()
  renderCategoryChart()
  renderKbStatusChart()
}

// 问答趋势折线图
const renderTrendChart = () => {
  if (!trendChartRef.value || !data.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)

  const trend = data.value.dailyTrend || []
  trendChart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: '#1e293b', borderColor: '#334155', textStyle: { color: '#e2e8f0' } },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: trend.map(t => t.date),
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f1f5f9' } }
    },
    series: [{
      data: trend.map(t => t.count),
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#3b82f6', width: 2.5 },
      itemStyle: { color: '#3b82f6', borderWidth: 2, borderColor: '#fff' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(59,130,246,0.3)' },
          { offset: 1, color: 'rgba(59,130,246,0.02)' }
        ])
      }
    }]
  })
}

// 用户角色饼图
const renderRoleChart = () => {
  if (!roleChartRef.value || !data.value) return
  if (!roleChart) roleChart = echarts.init(roleChartRef.value)

  roleChart.setOption({
    tooltip: { trigger: 'item', backgroundColor: '#1e293b', borderColor: '#334155', textStyle: { color: '#e2e8f0' } },
    legend: { bottom: 10, textStyle: { color: '#64748b', fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['50%', '45%'],
      data: data.value.userRoleDistribution || [],
      color: CHART_COLORS,
      label: { show: false },
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.2)' }
      }
    }]
  })
}

// 知识库分类饼图
const renderCategoryChart = () => {
  if (!categoryChartRef.value || !data.value) return
  if (!categoryChart) categoryChart = echarts.init(categoryChartRef.value)

  categoryChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 切片 ({d}%)', backgroundColor: '#1e293b', borderColor: '#334155', textStyle: { color: '#e2e8f0' } },
    legend: { orient: 'vertical', right: 10, top: 'center', textStyle: { color: '#64748b', fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['38%', '50%'],
      data: data.value.categoryDistribution?.length
        ? data.value.categoryDistribution
        : [{ name: '暂无数据', value: 1 }],
      color: CHART_COLORS,
      label: { show: false },
      emphasis: { itemStyle: { shadowBlur: 8 } }
    }]
  })
}

// 知识库状态柱状图
const renderKbStatusChart = () => {
  if (!kbStatusChartRef.value || !data.value) return
  if (!kbStatusChart) kbStatusChart = echarts.init(kbStatusChartRef.value)

  const statusData = data.value.knowledgeStatusStats || []
  const statusColors: Record<string, string> = {
    '上传中': '#f59e0b', '处理中': '#3b82f6', '就绪': '#10b981', '失败': '#ef4444'
  }

  kbStatusChart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: '#1e293b', borderColor: '#334155', textStyle: { color: '#e2e8f0' } },
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: statusData.map(s => s.name),
      axisLabel: { color: '#94a3b8' }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: '#f1f5f9' } }
    },
    series: [{
      type: 'bar',
      data: statusData.map(s => ({
        value: s.value,
        itemStyle: { color: statusColors[s.name] || '#94a3b8', borderRadius: [6, 6, 0, 0] }
      })),
      barWidth: '45%'
    }]
  })
}

// ===================== 工具函数 =====================
const WORD_COLORS = [
  '#2563eb', '#7c3aed', '#0891b2', '#059669',
  '#dc2626', '#9333ea', '#0284c7', '#16a34a',
  '#db2777', '#d97706', '#4f46e5', '#0d9488'
]

const getWordStyle = (value: number, index: number, name: string) => {
  const keywords = data.value?.hotKeywords || []
  const maxVal = Math.max(...keywords.map(k => k.value), 1)
  const minVal = Math.min(...keywords.map(k => k.value), 1)
  const ratio = maxVal === minVal ? 0.5 : (value - minVal) / (maxVal - minVal)

  // 字号范围：14px（最少）~ 52px（最多）
  const fontSize = Math.round(14 + ratio * 38)

  // 用词本身的字符码做确定性"随机"旋转，刷新不变
  const hash = name.split('').reduce((acc, c) => acc + c.charCodeAt(0), index * 7)
  const rotation = (hash % 41) - 20          // -20 ~ +20 deg
  const colorIdx = (index + Math.floor(hash / 13)) % WORD_COLORS.length

  return {
    fontSize: `${fontSize}px`,
    color: WORD_COLORS[colorIdx],
    opacity: 0.5 + ratio * 0.5,              // 0.5 ~ 1.0
    fontWeight: ratio > 0.6 ? '700' : ratio > 0.25 ? '600' : '500',
    transform: `rotate(${rotation}deg)`,
    display: 'inline-block',
    lineHeight: '1.3',
    padding: '4px 6px',
    cursor: 'default',
    transition: 'all 0.25s ease',
    userSelect: 'none' as const
  }
}

onMounted(loadData)

// 窗口缩放时重绘图表
window.addEventListener('resize', () => {
  trendChart?.resize()
  roleChart?.resize()
  categoryChart?.resize()
  kbStatusChart?.resize()
})
</script>

<style scoped>
.dashboard-page { display: flex; flex-direction: column; gap: 0; }

/* 顶部标题栏 */
.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.header-right { display: flex; align-items: center; gap: 10px; }
.dashboard-title { font-size: 22px; font-weight: 700; color: #1e293b; }

/* 指标卡 */
.metric-row { margin-bottom: 16px; }

.metric-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: var(--card-shadow);
  position: relative;
  overflow: hidden;
  transition: var(--transition);
  border-top: 3px solid var(--accent);
}
.metric-card:hover {
  box-shadow: var(--card-shadow-hover);
  transform: translateY(-2px);
}

.metric-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.metric-value {
  font-size: 30px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1;
}
.metric-label { font-size: 13px; color: #64748b; margin-top: 4px; }
.metric-sub { font-size: 11px; color: #94a3b8; margin-top: 2px; }

.metric-wave {
  position: absolute;
  right: -20px;
  bottom: -20px;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--accent);
  opacity: 0.05;
}

/* 图表行 */
.chart-row { margin-bottom: 16px; }

.chart-card { border-radius: 12px; }

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}

.chart-container {
  height: 260px;
  width: 100%;
}

/* 检索质量 */
.quality-stats {
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.quality-item { }
.quality-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
  font-weight: 500;
}

.quality-detail {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-top: 4px;
}

.qd-item {
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px;
  text-align: center;
}
.qd-val {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
}
.qd-val.positive { color: #10b981; }
.qd-val.negative { color: #ef4444; }
.qd-val.warning { color: #f59e0b; }
.qd-label { font-size: 11px; color: #94a3b8; margin-top: 3px; }

/* 词云 */
.word-cloud {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 6px 16px;
  padding: 24px 16px;
  min-height: 180px;
}

.word-item:hover {
  opacity: 1 !important;
  transform: rotate(0deg) scale(1.18) !important;
  filter: brightness(1.2);
  z-index: 1;
}

.empty-keywords {
  width: 100%;
  text-align: center;
  color: #cbd5e1;
  font-size: 14px;
  padding: 20px 0;
}
</style>
