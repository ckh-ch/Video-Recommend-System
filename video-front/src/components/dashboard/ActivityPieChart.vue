<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>用户活跃等级</div>
    <div ref="chartRef" class="chart-body"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Array, default: () => [] } })
const chartRef = ref(null)
let chart = null

const levelColors = ['#22C55E','#3B82F6','#F59E0B','#F97316','#EF4444']

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: 'Lv.{b}: {c}人 ({d}%)' },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['50%', '55%'],
      label: { color: '#94A3B8', fontSize: 11, formatter: 'Lv.{b}\n{d}%' },
      data: props.data.map(d => ({
        name: 'Lv.' + d.level,
        value: d.count,
        itemStyle: { color: levelColors[d.level - 1] || '#6366F1' }
      }))
    }]
  })
}

onMounted(render)
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => chart?.dispose())
</script>

<style scoped>
.chart-box { background: rgba(15,23,42,.8); border: 1px solid rgba(59,130,246,.15); border-radius: 12px; padding: 16px; backdrop-filter: blur(10px); }
.chart-title { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #F8FAFC; font-weight: 600; margin-bottom: 8px; }
.title-dot { width: 3px; height: 14px; background: #22C55E; border-radius: 2px; }
.chart-body { height: 260px; }
</style>
