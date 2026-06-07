<template>
  <div>
    <h2>个性化推荐</h2>
    <div class="user-bar">
      <label>用户ID：</label>
      <input v-model.number="userId" type="number" />
      <button @click="loadRecommend">刷新</button>
    </div>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="videos.length === 0" class="empty">暂无推荐</div>
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
import { useRoute } from 'vue-router'
import { getPersonalizedRecommend } from '../api/index.js'

const route = useRoute()
const userId = ref(parseInt(route.params.userId) || 85500)
const videos = ref([])
const loading = ref(false)

async function loadRecommend() {
  loading.value = true
  try {
    const res = await getPersonalizedRecommend(userId.value)
    videos.value = res.data
  } catch {
    videos.value = []
  }
  loading.value = false
}

onMounted(loadRecommend)
</script>

<style scoped>
h2 { color: #333; font-size: 20px; margin-bottom: 16px; }
.user-bar { margin-bottom: 20px; display: flex; align-items: center; gap: 8px; }
.user-bar input { width: 100px; padding: 6px 10px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }
.user-bar button { padding: 6px 16px; background: #fb7299; color: white; border: none; border-radius: 4px; cursor: pointer; }
.user-bar button:hover { background: #e8648a; }
.video-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.video-card { background: white; border-radius: 8px; padding: 20px 16px; cursor: pointer; transition: transform .15s, box-shadow .15s; }
.video-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,.1); }
.card-category { font-size: 14px; color: #fb7299; font-weight: bold; margin-bottom: 4px; }
.card-tags { font-size: 12px; color: #666; }
.card-id { font-size: 11px; color: #999; margin-top: 4px; }
.loading, .empty { text-align: center; color: #999; padding: 40px; font-size: 15px; }
</style>
