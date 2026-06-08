<template>
  <div class="kpi-grid">
    <div class="kpi-card" v-for="item in kpis" :key="item.label">
      <div class="kpi-icon" :style="{ background: item.iconBg }">{{ item.icon }}</div>
      <div class="kpi-info">
        <div class="kpi-value">{{ item.value.toLocaleString() }}</div>
        <div class="kpi-label">{{ item.label }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  summary: { type: Object, default: () => ({}) }
})

const kpis = computed(() => [
  { label: '视频总数', value: props.summary.totalVideos || 0, icon: '🎬', iconBg: 'rgba(59,130,246,.2)' },
  { label: '用户总数', value: props.summary.totalUsers || 0, icon: '👤', iconBg: 'rgba(251,114,153,.2)' },
  { label: '行为总数', value: props.summary.totalBehaviors || 0, icon: '📊', iconBg: 'rgba(34,197,94,.2)' },
  { label: '分类总数', value: props.summary.totalCategories || 0, icon: '📁', iconBg: 'rgba(245,158,11,.2)' }
])
</script>

<style scoped>
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.kpi-card {
  background: rgba(15,23,42,.8); border: 1px solid rgba(59,130,246,.15);
  border-radius: 12px; padding: 20px; display: flex; align-items: center; gap: 16px;
  backdrop-filter: blur(10px); transition: transform .2s, border-color .2s;
}
.kpi-card:hover { transform: translateY(-2px); border-color: rgba(59,130,246,.4); }
.kpi-icon {
  width: 48px; height: 48px; border-radius: 12px; display: flex;
  align-items: center; justify-content: center; font-size: 22px; flex-shrink: 0;
}
.kpi-value { font-family: 'Fira Code', monospace; font-size: 26px; font-weight: 700; color: #F8FAFC; line-height: 1.2; }
.kpi-label { font-size: 13px; color: #94A3B8; margin-top: 2px; }
</style>
