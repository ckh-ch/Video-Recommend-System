<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>实时行为动态</div>
    <div class="realtime-body" ref="listRef">
      <div v-if="actions.length === 0" class="empty">等待实时数据...</div>
      <div v-for="(act, i) in actions" :key="i" class="action-item" :style="{ animationDelay: i * 0.05 + 's' }">
        <span class="action-user">#{{ act.userId }}</span>
        <span :class="['action-type', act.action === 'like' ? 'like' : 'view']">{{ act.action === 'like' ? '点赞' : '观看' }}</span>
        <span class="action-cat">{{ act.category }}</span>
        <span class="action-time">{{ act.time?.split(' ')[1] || '' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { get } from '../../api/index.js'

const props = defineProps({ userId: { type: Number, default: null } })
const allActions = ref([])
const listRef = ref(null)
let timer = null

const actions = computed(() => {
  if (!props.userId) return allActions.value.slice(0, 20)
  return allActions.value.filter(a => a.userId == props.userId).slice(0, 20)
})

async function fetchRealtime() {
  try {
    const res = await get('/dashboard/realtime')
    const raw = res.data.recentActions || []
    allActions.value = raw.map(s => {
      try { return JSON.parse(s) } catch { return { userId: '?', category: '?', action: 'view', time: '' } }
    })
  } catch { /* ignore */ }
}

onMounted(() => {
  fetchRealtime()
  timer = setInterval(fetchRealtime, 5000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.chart-box { background: rgba(15,23,42,.8); border: 1px solid rgba(59,130,246,.15); border-radius: 12px; padding: 16px; backdrop-filter: blur(10px); }
.chart-title { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #F8FAFC; font-weight: 600; margin-bottom: 8px; }
.title-dot { width: 3px; height: 14px; background: #F59E0B; border-radius: 2px; }
.realtime-body { height: 260px; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; }
.realtime-body::-webkit-scrollbar { width: 3px; }
.realtime-body::-webkit-scrollbar-thumb { background: rgba(59,130,246,.3); border-radius: 2px; }
.action-item {
  display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 6px;
  font-size: 12px; background: rgba(59,130,246,.05); animation: fadeIn .3s ease both;
}
.action-item:hover { background: rgba(59,130,246,.12); }
.action-user { color: #3B82F6; font-family: 'Fira Code', monospace; font-weight: 600; min-width: 50px; }
.action-type { padding: 1px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; }
.action-type.like { background: rgba(251,114,153,.2); color: #fb7299; }
.action-type.view { background: rgba(59,130,246,.15); color: #60A5FA; }
.action-cat { color: #94A3B8; flex: 1; }
.action-time { color: #64748B; font-family: 'Fira Code', monospace; font-size: 11px; }
.empty { color: #64748B; font-size: 13px; text-align: center; padding: 40px 0; }
@keyframes fadeIn { from { opacity: 0; transform: translateX(-8px); } to { opacity: 1; transform: translateX(0); } }
</style>
