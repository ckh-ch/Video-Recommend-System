<template>
  <div class="dashboard-card" style="display:flex;flex-direction:column;position:relative">
    <div class="dashboard-card-title"><span class="dashboard-card-dot" style="background:#fb7299"></span>推荐视频</div>
    <div v-if="loading" class="empty-state">加载中...</div>
    <div v-else-if="videos.length === 0" class="empty-state">暂无推荐</div>
    <div v-else class="scroll-container">
      <div class="scroll-track">
        <div v-for="video in videos" :key="video.id" class="video-card">
          <div class="video-card-icon">{{ iconMap[video.category] || '🎬' }}</div>
          <div class="video-card-cat">{{ video.category }}</div>
          <div class="video-card-id">#{{ video.id }}</div>
          <div class="card-actions">
            <button class="action-btn action-btn--like" @click.stop="doLike(video)">👍 点赞</button>
            <button class="action-btn action-btn--watch" @click.stop="doWatch(video)">👁️ 观看</button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="msg" class="toast-msg">{{ msg }}</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import api from '../../api/index.js'
import { iconMap } from '../../constants.js'
import { useToast } from '../../composables/useToast.js'

const props = defineProps({ userId: { type: Number, default: 85500 } })
const videos = ref([])
const loading = ref(false)
const msg = ref('')

async function load() {
  loading.value = true
  try {
    const res = await api.get(`/recommend/personalized/${props.userId}`, { params: { limit: 20 } })
    videos.value = res.data || []
  } catch {
    videos.value = []
    useToast().show('推荐视频加载失败', 'warning')
  }
  loading.value = false
}

function showMsg(text) {
  msg.value = text
  setTimeout(() => msg.value = '', 2000)
}

async function doLike(video) {
  try {
    await api.post('/behavior', {
      userId: props.userId, videoId: video.id, videoCategory: video.category,
      likeType: 1, relayType: 0, viewingTime: 0
    })
    showMsg(`已点赞 #${video.id}`)
  } catch { showMsg('操作失败'); useToast().show(`操作失败 #${video.id}`, 'error', 3000) }
}

async function doWatch(video) {
  try {
    await api.post('/behavior', {
      userId: props.userId, videoId: video.id, videoCategory: video.category,
      likeType: 0, relayType: 0, viewingTime: 120
    })
    showMsg(`已记录观看 #${video.id}`)
  } catch { showMsg('操作失败'); useToast().show(`操作失败 #${video.id}`, 'error', 3000) }
}

watch(() => props.userId, () => load(), { immediate: true })
</script>

<style scoped>
</style>
