<template>
  <div class="chart-box">
    <div class="chart-title"><span class="title-dot"></span>推荐视频</div>
    <div v-if="loading" class="empty">加载中...</div>
    <div v-else-if="videos.length === 0" class="empty">暂无推荐</div>
    <div v-else class="scroll-wrap">
      <div class="scroll-track">
        <div v-for="video in videos" :key="video.id" class="video-card">
          <div class="card-icon">{{ iconMap[video.category] || '🎬' }}</div>
          <div class="card-cat">{{ video.category }}</div>
          <div class="card-id">#{{ video.id }}</div>
          <div class="card-actions">
            <button class="act-btn like" @click.stop="doLike(video)">👍 点赞</button>
            <button class="act-btn watch" @click.stop="doWatch(video)">👁️ 观看</button>
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
  } catch { showMsg('操作失败') }
}

async function doWatch(video) {
  try {
    await api.post('/behavior', {
      userId: props.userId, videoId: video.id, videoCategory: video.category,
      likeType: 0, relayType: 0, viewingTime: 120
    })
    showMsg(`已记录观看 #${video.id}`)
  } catch { showMsg('操作失败') }
}

watch(() => props.userId, () => load(), { immediate: true })
</script>

<style scoped>
.chart-box { background: rgba(15,23,42,.8); border: 1px solid rgba(59,130,246,.15); border-radius: 12px; padding: 16px; backdrop-filter: blur(10px); position: relative; display: flex; flex-direction: column; }
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
.card-actions { display: flex; gap: 4px; margin-top: 4px; }
.act-btn {
  padding: 3px 8px; border: none; border-radius: 4px; font-size: 10px; cursor: pointer;
  transition: all .15s;
}
.act-btn.like { background: rgba(251,114,153,.15); color: #fb7299; }
.act-btn.like:hover { background: rgba(251,114,153,.3); }
.act-btn.watch { background: rgba(59,130,246,.15); color: #60A5FA; }
.act-btn.watch:hover { background: rgba(59,130,246,.3); }
.toast-msg {
  position: absolute; bottom: 12px; right: 16px; padding: 6px 14px;
  background: rgba(34,197,94,.2); border: 1px solid rgba(34,197,94,.3);
  border-radius: 6px; color: #4ADE80; font-size: 12px; animation: fadeIn .2s ease;
}
.empty { color: #64748B; font-size: 13px; text-align: center; padding: 20px 0; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }
</style>
