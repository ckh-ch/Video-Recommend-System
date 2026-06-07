<template>
  <div>
    <h2>热门视频</h2>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="videos.length === 0" class="empty">暂无数据</div>
    <div v-else class="video-grid">
      <div v-for="video in videos" :key="video.id" class="video-card" @click="$router.push(`/video/${video.id}`)">
        <div class="card-category">{{ video.category }}</div>
        <div class="card-tags">{{ video.tags }}</div>
        <div class="card-id">视频 #{{ video.id }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getHotVideos } from '../api/index.js'

const videos = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getHotVideos(20)
    videos.value = res.data
  } catch {
    videos.value = []
  }
  loading.value = false
}

onMounted(load)
</script>

<style scoped>
h2 { color: #333; font-size: 20px; margin-bottom: 16px; }
.video-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.video-card { background: white; border-radius: 8px; padding: 20px 16px; cursor: pointer; transition: transform .15s; }
.video-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,.1); }
.card-category { font-size: 14px; color: #fb7299; font-weight: bold; }
.card-tags { font-size: 12px; color: #666; margin-top: 4px; }
.loading, .empty { text-align: center; color: #999; padding: 40px; }
</style>
