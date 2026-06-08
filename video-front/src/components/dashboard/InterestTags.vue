<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>用户兴趣标签</div>
    <div v-if="loading" class="empty">加载中...</div>
    <div v-else-if="tags.length === 0" class="empty">暂无兴趣数据</div>
    <div v-else class="tags-scroll">
      <div class="tags-wrap">
        <div v-for="tag in tags" :key="tag.name" class="tag-chip" :style="{ fontSize: tagSize(tag.count) + 'px' }">
        <span class="tag-name">{{ tag.name }}</span>
        <span class="tag-count">{{ tag.count }}</span>
      </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import api from '../../api/index.js'

const props = defineProps({ userId: { type: Number, default: 85500 } })
const tags = ref([])
const loading = ref(false)
const maxCount = ref(1)

async function load() {
  loading.value = true
  try {
    const res = await api.get(`/dashboard/user-interest/${props.userId}`)
    tags.value = (res.data.tags || []).slice(0, 20)
    maxCount.value = tags.value.length > 0 ? tags.value[0].count : 1
  } catch {
    tags.value = []
  }
  loading.value = false
}

function tagSize(count) {
  const min = 12, max = 22
  const ratio = maxCount.value > 1 ? Math.log(count) / Math.log(maxCount.value) : 0.5
  return min + (max - min) * Math.max(0.2, ratio)
}

watch(() => props.userId, () => load(), { immediate: true })
</script>

<style scoped>
.chart-box { background: rgba(15,23,42,.8); border: 1px solid rgba(59,130,246,.15); border-radius: 12px; padding: 16px; backdrop-filter: blur(10px); display: flex; flex-direction: column; }
.chart-title { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #F8FAFC; font-weight: 600; margin-bottom: 10px; }
.title-dot { width: 3px; height: 14px; background: #A855F7; border-radius: 2px; }
.tags-scroll { flex: 1; min-height: 0; overflow-y: auto; }
.tags-scroll::-webkit-scrollbar { width: 3px; }
.tags-scroll::-webkit-scrollbar-thumb { background: rgba(59,130,246,.3); border-radius: 2px; }
.tags-wrap { display: flex; flex-wrap: wrap; gap: 6px; align-content: flex-start; }
.tag-chip { display: inline-flex; align-items: center; gap: 4px; padding: 3px 10px; border-radius: 16px; background: rgba(59,130,246,.1); border: 1px solid rgba(59,130,246,.2); transition: all .2s; }
.tag-chip:hover { background: rgba(59,130,246,.2); transform: translateY(-1px); }
.tag-name { color: #E2E8F0; font-weight: 500; }
.tag-count { color: #64748B; font-size: .85em; }
.empty { color: #64748B; font-size: 13px; text-align: center; padding: 20px 0; }
</style>
