<template>
  <div class="dashboard-card">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#22C55E"></span>推荐覆盖概览</div>
    <div ref="chartRef" class="dashboard-card-body"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { init, graphic } from '../../libs/echarts.js'
import api from '../../api/index.js'
import { useToast } from '../../composables/useToast.js'

const chartRef = ref(null)
let chart = null
let resizeObserver = null

async function load() {
  if (!chartRef.value) return
  if (!chart) chart = init(chartRef.value)

  try {
    const res = await api.get('/dashboard/recommend-overview')
    const d = res.data
    const recommended = d.totalRecommends || 0
    const notRecommended = (d.totalUsers || 0) - recommended

    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
      series: [{
        type: 'pie', radius: ['45%', '70%'], center: ['50%', '55%'],
        label: {
          color: '#94A3B8', fontSize: 11,
          formatter: (p) => p.name === '已覆盖' ? `已覆盖\n${(d.coverage * 100).toFixed(0)}%` : `未覆盖\n${((1 - d.coverage) * 100).toFixed(0)}%`
        },
        data: [
          { name: '已覆盖', value: recommended, itemStyle: { color: '#22C55E' } },
          { name: '未覆盖', value: Math.max(0, notRecommended), itemStyle: { color: '#1E293B' } }
        ],
        emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(34,197,94,.3)' } }
      }]
    })
  } catch { useToast().show('推荐覆盖数据加载失败', 'warning') }
}

onMounted(() => {
  load()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  if (chartRef.value) resizeObserver.observe(chartRef.value)
})
onBeforeUnmount(() => { resizeObserver?.disconnect(); chart?.dispose() })
</script>

<style scoped>
</style>
