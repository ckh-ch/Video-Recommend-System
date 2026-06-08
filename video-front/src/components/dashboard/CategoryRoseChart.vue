<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>视频分类分布</div>
    <div ref="chartRef" class="chart-body"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({ data: { type: Array, default: () => [] } })
const chartRef = ref(null)
let chart = null

const colors = ['#3B82F6','#F59E0B','#22C55E','#fb7299','#8B5CF6','#06B6D4','#EC4899','#14B8A6','#F97316','#6366F1','#84CC16','#A855F7']

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    series: [{
      type: 'pie', radius: ['20%', '70%'], center: ['50%', '55%'],
      roseType: 'area', itemStyle: { borderRadius: 4 },
      label: { color: '#94A3B8', fontSize: 11, formatter: '{b}\n{d}%' },
      data: props.data.map((d, i) => ({ ...d, itemStyle: { color: colors[i % colors.length] } }))
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
.title-dot { width: 3px; height: 14px; background: #3B82F6; border-radius: 2px; }
.chart-body { height: 260px; }
</style>
