<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>各分类平均观看时长</div>
    <div ref="chartRef" class="chart-body"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Array, default: () => [] } })
const chartRef = ref(null)
let chart = null

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const sorted = [...props.data].sort((a, b) => a.avgViewTime - b.avgViewTime)
  chart.setOption({
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (p) => `${p[0].name}<br/>平均观看: ${(p[0].value / 60).toFixed(1)} 分钟`
    },
    grid: { top: 8, bottom: 8, left: 80, right: 40 },
    xAxis: { type: 'value', axisLabel: { color: '#94A3B8', fontSize: 10, formatter: v => (v/60).toFixed(0) + 'm' }, splitLine: { lineStyle: { color: '#1E293B' } } },
    yAxis: { type: 'category', data: sorted.map(d => d.category), axisLabel: { color: '#94A3B8', fontSize: 11 }, axisLine: { lineStyle: { color: '#1E293B' } } },
    series: [{
      type: 'bar', data: sorted.map(d => ({
        value: d.avgViewTime,
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#3B82F6' }, { offset: 1, color: '#8B5CF6' }
        ]), borderRadius: [0, 4, 4, 0] }
      })), barWidth: 12
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
.title-dot { width: 3px; height: 14px; background: #8B5CF6; border-radius: 2px; }
.chart-body { height: auto; min-height: 320px; }
</style>
