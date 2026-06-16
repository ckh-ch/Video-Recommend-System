<template>
  <div class="dashboard-card">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#8B5CF6"></span>各分类平均观看时长</div>
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
        itemStyle: { color: new graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#3B82F6' }, { offset: 1, color: '#8B5CF6' }
        ]), borderRadius: [0, 4, 4, 0] }
      })), barWidth: 12
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
.dashboard-card-body { height: auto; min-height: 320px; }
</style>
