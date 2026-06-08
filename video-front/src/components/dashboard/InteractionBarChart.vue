<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>分类互动率对比</div>
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

onMounted(render)
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => chart?.dispose())
</script>

<style scoped>
.chart-box { background: rgba(15,23,42,.8); border: 1px solid rgba(59,130,246,.15); border-radius: 12px; padding: 16px; backdrop-filter: blur(10px); }
.chart-title { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #F8FAFC; font-weight: 600; margin-bottom: 8px; }
.title-dot { width: 3px; height: 14px; background: #fb7299; border-radius: 2px; }
.chart-body { height: 260px; }
</style>
