<template>
  <div class="dashboard-card" style="display:flex;flex-direction:column">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#fb7299"></span>热门视频</div>
    <div v-if="loading" class="empty-state">加载中...</div>
    <div v-else-if="videos.length === 0" class="empty-state">暂无热门视频</div>
    <div v-else class="scroll-container">
      <div class="scroll-track">
        <div v-for="video in videos" :key="video.id" class="video-card">
          <div class="video-card-icon">{{ iconMap[video.category] || '🎬' }}</div>
          <div class="video-card-cat">{{ video.category }}</div>
          <div class="video-card-id">#{{ video.id }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api/index.js'
import { iconMap } from '../../constants.js'
import { useToast } from '../../composables/useToast.js'

const videos = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.get('/recommend/hot', { params: { limit: 20 } })
    videos.value = res.data || []
  } catch {
    videos.value = []
    useToast().show('热门视频加载失败', 'warning')
  }
  loading.value = false
}

onMounted(load)
</script>
