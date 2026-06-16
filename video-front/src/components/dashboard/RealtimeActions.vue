<template>
  <div class="dashboard-card" style="display:flex;flex-direction:column">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#F59E0B"></span>实时行为动态</div>
    <div class="scroll-vertical" ref="listRef" style="min-height:260px;max-height:320px">
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
import { useToast } from '../../composables/useToast.js'

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
  } catch { const t = useToast(); t.show('实时数据加载失败', 'warning') }
}

onMounted(() => {
  fetchRealtime()
  timer = setInterval(fetchRealtime, 5000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
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
</style>
