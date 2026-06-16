<template>
  <div class="dashboard-card">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#22C55E"></span>用户活跃等级</div>
    <div ref="chartRef" class="dashboard-card-body"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { init, graphic } from '../../libs/echarts.js'

const props = defineProps({ data: { type: Array, default: () => [] } })
const chartRef = ref(null)
let chart = null

const levelColors = ['#22C55E','#3B82F6','#F59E0B','#F97316','#EF4444']

let resizeObserver = null

function render() {
  if (!chartRef.value) return
  if (!chart) chart = init(chartRef.value)
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
