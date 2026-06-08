<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>热门视频</div>
    <div v-if="loading" class="empty">加载中...</div>
    <div v-else-if="videos.length === 0" class="empty">暂无热门视频</div>
    <div v-else class="scroll-wrap">
      <div class="scroll-track">
        <div v-for="video in videos" :key="video.id" class="video-card">
          <div class="card-icon">{{ iconMap[video.category] || '🎬' }}</div>
          <div class="card-cat">{{ video.category }}</div>
          <div class="card-id">#{{ video.id }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api/index.js'
import { iconMap } from '../../constants.js'

const videos = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.get('/recommend/hot', { params: { limit: 20 } })
    videos.value = res.data || []
  } catch {
    videos.value = []
  }
  loading.value = false
}

onMounted(load)
</script>

<style scoped>
.chart-box { background: rgba(15,23,42,.8); border: 1px solid rgba(59,130,246,.15); border-radius: 12px; padding: 16px; backdrop-filter: blur(10px); display: flex; flex-direction: column; }
.chart-title { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #F8FAFC; font-weight: 600; margin-bottom: 10px; flex-shrink: 0; }
.title-dot { width: 3px; height: 14px; background: #fb7299; border-radius: 2px; }
.scroll-wrap { overflow-x: auto; overflow-y: hidden; flex: 1; min-height: 0; padding-bottom: 4px; }
.scroll-wrap::-webkit-scrollbar { height: 3px; }
.scroll-wrap::-webkit-scrollbar-thumb { background: rgba(59,130,246,.3); border-radius: 2px; }
.scroll-track { display: flex; gap: 12px; min-width: min-content; }
.video-card {
  min-width: 155px; max-width: 155px; background: rgba(30,41,59,.5); border: 1px solid rgba(59,130,246,.1);
  border-radius: 10px; padding: 14px 12px; cursor: pointer; transition: all .2s; flex-shrink: 0;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
}
.video-card:hover { transform: translateY(-3px); border-color: rgba(59,130,246,.4); box-shadow: 0 6px 20px rgba(0,0,0,.3); }
.card-icon { font-size: 32px; }
.card-cat { font-size: 13px; color: #fb7299; font-weight: 600; }
.card-id { font-size: 11px; color: #64748B; }
.empty { color: #64748B; font-size: 13px; text-align: center; padding: 20px 0; }
</style>
