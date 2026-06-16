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

const { displayValue: totalWatch, animate: animateWatch } = useCountUp(0)
const { displayValue: totalCategories, animate: animateCategories } = useCountUp(0)
const { displayValue: totalViewTime, animate: animateViewTime } = useCountUp(0)
const { displayValue: totalLikes, animate: animateLikes } = useCountUp(0)

function animateAll() {
  animateWatch(props.summary.totalBehaviors || 0)
  animateCategories(props.summary.totalCategories || 0)
  animateViewTime((props.summary.totalViewTime || 0) / 60)
  animateLikes(props.summary.totalLikes || 0)
}

function formatViewTime(val) {
  return val.toFixed(0) + '分钟'
}

onMounted(animateAll)
watch(() => props.summary, animateAll, { deep: true })

const kpis = computed(() => [
  {
    label: '观看次数',
    display: totalWatch.value.toLocaleString(),
    icon: '👁️',
    iconBg: 'rgba(59,130,246,.2)'
  },
  {
    label: '观看分类',
    display: totalCategories.value.toLocaleString(),
    icon: '📁',
    iconBg: 'rgba(245,158,11,.2)'
  },
  {
    label: '观看时长',
    display: formatViewTime(totalViewTime.value),
    icon: '⏱️',
    iconBg: 'rgba(34,197,94,.2)'
  },
  {
    label: '点赞数',
    display: totalLikes.value.toLocaleString(),
    icon: '👍',
    iconBg: 'rgba(251,114,153,.2)'
  }
])
</script>

<style scoped>
</style>
