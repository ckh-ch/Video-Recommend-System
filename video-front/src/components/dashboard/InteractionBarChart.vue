<template>
  <div class="dashboard-card">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#fb7299"></span>分类互动率对比</div>
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
  const cats = props.data.map(d => d.category)
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const cat = params[0].axisValue
        const item = props.data.find(d => d.category === cat)
        return `${cat}<br/>点赞率: ${(item.likeRate * 100).toFixed(1)}%<br/>转发率: ${(item.relayRate * 100).toFixed(1)}%<br/>行为数: ${item.behaviorCount}`
      }
    },
    legend: { data: ['点赞率','转发率'], textStyle: { color: '#94A3B8', fontSize: 11 }, top: 0, right: 0 },
    grid: { top: 28, bottom: 20, left: 50, right: 16 },
    xAxis: { type: 'category', data: cats, axisLabel: { color: '#94A3B8', fontSize: 10, rotate: cats.length > 8 ? 35 : 0 }, axisLine: { lineStyle: { color: '#1E293B' } } },
    yAxis: { type: 'value', axisLabel: { color: '#94A3B8', fontSize: 10, formatter: '{value}%' }, splitLine: { lineStyle: { color: '#1E293B' } } },
    series: [
      { name: '点赞率', type: 'bar', data: props.data.map(d => +(d.likeRate * 100).toFixed(1)), itemStyle: { color: '#3B82F6', borderRadius: [4,4,0,0] }, barWidth: '30%' },
      { name: '转发率', type: 'bar', data: props.data.map(d => +(d.relayRate * 100).toFixed(1)), itemStyle: { color: '#fb7299', borderRadius: [4,4,0,0] }, barWidth: '30%' }
    ]
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
