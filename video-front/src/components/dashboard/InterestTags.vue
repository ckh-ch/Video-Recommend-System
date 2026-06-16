<template>
  <div class="dashboard-card" style="display:flex;flex-direction:column">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#A855F7"></span>用户兴趣标签</div>
    <div v-if="loading" class="empty-state">加载中...</div>
    <div v-else-if="tags.length === 0" class="empty-state">暂无兴趣数据</div>
    <div v-else class="scroll-vertical">
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
import { useToast } from '../../composables/useToast.js'

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
    useToast().show('兴趣标签加载失败', 'warning')
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
.tags-wrap { display: flex; flex-wrap: wrap; gap: 6px; align-content: flex-start; padding: 4px 0; }
</style>
