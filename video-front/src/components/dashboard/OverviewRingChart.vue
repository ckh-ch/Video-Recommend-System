<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>推荐覆盖概览</div>
    <div ref="chartRef" class="chart-body"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import api from '../../api/index.js'

const chartRef = ref(null)
let chart = null

async function load() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

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
  } catch { /* ignore */ }
}

onMounted(load)
onBeforeUnmount(() => chart?.dispose())
</script>

<style scoped>
.chart-box { background: rgba(15,23,42,.8); border: 1px solid rgba(59,130,246,.15); border-radius: 12px; padding: 16px; backdrop-filter: blur(10px); }
.chart-title { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #F8FAFC; font-weight: 600; margin-bottom: 8px; }
.title-dot { width: 3px; height: 14px; background: #22C55E; border-radius: 2px; }
.chart-body { height: 260px; }
</style>
