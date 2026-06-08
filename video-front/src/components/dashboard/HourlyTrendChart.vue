<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>小时级行为趋势</div>
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
  const hours = Array.from({length: 24}, (_, i) => `${i}时`)
  const values = hours.map((_, i) => { const d = props.data.find(x => x.hour === i); return d ? d.count : 0 })
  chart.setOption({
    tooltip: { trigger: 'axis', formatter: (p) => `${p[0].axisValue}<br/>行为数: ${p[0].value}` },
    grid: { top: 8, bottom: 20, left: 44, right: 16 },
    xAxis: { type: 'category', data: hours, axisLabel: { color: '#94A3B8', fontSize: 9, interval: 2, rotate: 30 }, axisLine: { lineStyle: { color: '#1E293B' } } },
    yAxis: { type: 'value', axisLabel: { color: '#94A3B8', fontSize: 10 }, splitLine: { lineStyle: { color: '#1E293B' } } },
    series: [{
      type: 'line', smooth: true, data: values, symbol: 'circle', symbolSize: 4,
      lineStyle: { color: '#3B82F6', width: 2 },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(59,130,246,.3)' }, { offset: 1, color: 'rgba(59,130,246,.02)' }
      ]) },
      itemStyle: { color: '#3B82F6' }
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
.title-dot { width: 3px; height: 14px; background: #06B6D4; border-radius: 2px; }
.chart-body { height: 260px; }
</style>
