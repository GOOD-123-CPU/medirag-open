<template>
  <!--
    MediRAG 品牌图标
    设计语言：医疗十字（圆角臂）+ 四角神经网络节点 + 斜向连线
    代表：医疗健康 × AI 知识检索
    用法：<MediRagIcon :size="40" color="#fff" />
  -->
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <!-- 十字竖臂（圆角） -->
    <rect x="12.5" y="2.5" width="7" height="27" rx="3.5" :fill="color" />
    <!-- 十字横臂（圆角） -->
    <rect x="2.5" y="12.5" width="27" height="7" rx="3.5" :fill="color" />

    <!-- 四角神经节点 -->
    <circle cx="5.5" cy="5.5" r="2.5" :fill="color" :opacity="nodeOpacity" />
    <circle cx="26.5" cy="5.5" r="2.5" :fill="color" :opacity="nodeOpacity" />
    <circle cx="5.5" cy="26.5" r="2.5" :fill="color" :opacity="nodeOpacity" />
    <circle cx="26.5" cy="26.5" r="2.5" :fill="color" :opacity="nodeOpacity" />

    <!-- 对角连线（节点 → 十字臂边缘） -->
    <line x1="7.5" y1="7.5" x2="12.5" y2="12.5"
      :stroke="color" stroke-width="1.2" :stroke-opacity="lineOpacity" stroke-linecap="round" />
    <line x1="24.5" y1="7.5" x2="19.5" y2="12.5"
      :stroke="color" stroke-width="1.2" :stroke-opacity="lineOpacity" stroke-linecap="round" />
    <line x1="7.5" y1="24.5" x2="12.5" y2="19.5"
      :stroke="color" stroke-width="1.2" :stroke-opacity="lineOpacity" stroke-linecap="round" />
    <line x1="24.5" y1="24.5" x2="19.5" y2="19.5"
      :stroke="color" stroke-width="1.2" :stroke-opacity="lineOpacity" stroke-linecap="round" />
  </svg>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  size?: number | string
  color?: string
  nodeOpacity?: number
  lineOpacity?: number
}>(), {
  size: 32,
  color: '#ffffff',
  nodeOpacity: 0.55,
  lineOpacity: 0.4,
})
</script>
