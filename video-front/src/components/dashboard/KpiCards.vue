<template>
  <div class="kpi-grid">
    <div class="kpi-card" v-for="item in kpis" :key="item.label">
      <div class="kpi-icon" :style="{ background: item.iconBg }">{{ item.icon }}</div>
      <div class="kpi-info">
        <div class="kpi-value">{{ item.display }}</div>
        <div class="kpi-label">{{ item.label }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, watch, onMounted } from 'vue'
import { useCountUp } from '../../composables/useCountUp.js'

const props = defineProps({
  summary: { type: Object, default: () => ({}) }
})

const { displayValue: totalVideos, animate: animateVideos } = useCountUp(0)
const { displayValue: totalUsers, animate: animateUsers } = useCountUp(0)
const { displayValue: totalBehaviors, animate: animateBehaviors } = useCountUp(0)
const { displayValue: totalCategories, animate: animateCategories } = useCountUp(0)

function animateAll() {
  animateVideos(props.summary.totalVideos || 0)
  animateUsers(props.summary.totalUsers || 0)
  animateBehaviors(props.summary.totalBehaviors || 0)
  animateCategories(props.summary.totalCategories || 0)
}

onMounted(animateAll)
watch(() => props.summary, animateAll, { deep: true })

const kpis = computed(() => [
  { label: '视频总数', display: totalVideos.value.toLocaleString(), icon: '🎬', iconBg: 'rgba(59,130,246,.2)' },
  { label: '用户总数', display: totalUsers.value.toLocaleString(), icon: '👤', iconBg: 'rgba(251,114,153,.2)' },
  { label: '行为总数', display: totalBehaviors.value.toLocaleString(), icon: '📊', iconBg: 'rgba(34,197,94,.2)' },
  { label: '分类总数', display: totalCategories.value.toLocaleString(), icon: '📁', iconBg: 'rgba(245,158,11,.2)' }
])
</script>

<style scoped>
</style>
