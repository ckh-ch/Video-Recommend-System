<template>
  <div class="dashboard-card">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#3B82F6"></span>视频分类分布</div>
    <div ref="chartRef" class="dashboard-card-body"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { init, graphic } from '../../libs/echarts.js'

const props = defineProps({ data: { type: Array, default: () => [] } })
const chartRef = ref(null)
let chart = null

const colors = ['#3B82F6','#F59E0B','#22C55E','#fb7299','#8B5CF6','#06B6D4','#EC4899','#14B8A6','#F97316','#6366F1','#84CC16','#A855F7']

let resizeObserver = null

function render() {
  if (!chartRef.value) return
  if (!chart) chart = init(chartRef.value)
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
