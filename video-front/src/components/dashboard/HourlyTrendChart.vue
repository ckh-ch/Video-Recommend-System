<template>
  <div class="dashboard-card">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#06B6D4"></span>小时级行为趋势</div>
    <div ref="chartRef" class="dashboard-card-body"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { init, graphic } from '../../libs/echarts.js'

const props = defineProps({ data: { type: Array, default: () => [] } })
const chartRef = ref(null)
let chart = null

let resizeObserver = null

function render() {
  if (!chartRef.value) return
  if (!chart) chart = init(chartRef.value)
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
      areaStyle: { color: new graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(59,130,246,.3)' }, { offset: 1, color: 'rgba(59,130,246,.02)' }
      ]) },
      itemStyle: { color: '#3B82F6' }
    }]
  })
}

onMounted(() => {
  render()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  if (chartRef.value) resizeObserver.observe(chartRef.value)
})
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => { resizeObserver?.disconnect(); chart?.dispose() })
</script>

<style scoped>
</style>
