<template>
  <div>
    <button class="back-btn" @click="$router.back()">返回</button>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="!video" class="empty">视频不存在</div>
    <div v-else class="detail">
      <h2>{{ video.category }} - #{{ video.id }}</h2>
      <div class="info">
        <div class="info-item"><label>分类</label><span>{{ video.category }}</span></div>
        <div class="info-item"><label>标签</label><span>{{ video.tags }}</span></div>
      </div>
      <div class="actions">
        <button class="action-btn" @click="like">点赞</button>
        <button class="action-btn" @click="watch">记录观看</button>
      </div>
      <div v-if="msg" class="msg">{{ msg }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getVideoDetail, recordBehavior } from '../api/index.js'

const route = useRoute()
const video = ref(null)
const loading = ref(false)
const msg = ref('')

async function load() {
  loading.value = true
  try {
    const res = await getVideoDetail(route.params.id)
    video.value = res.data
  } catch {
    video.value = null
  }
  loading.value = false
}

async function like() {
  try {
    await recordBehavior({ userId: 85500, videoId: parseInt(route.params.id), videoCategory: video.value.category, likeType: 1, viewingTime: 0 })
    msg.value = '已点赞'
  } catch { msg.value = '操作失败' }
}

async function watch() {
  try {
    await recordBehavior({ userId: 85500, videoId: parseInt(route.params.id), videoCategory: video.value.category, likeType: 0, viewingTime: 120 })
    msg.value = '已记录观看'
  } catch { msg.value = '操作失败' }
}

onMounted(load)
</script>

<style scoped>
.back-btn { padding: 6px 16px; background: #eee; border: none; border-radius: 4px; cursor: pointer; margin-bottom: 16px; }
.detail { background: white; border-radius: 8px; padding: 24px; }
h2 { color: #333; font-size: 20px; margin: 0 0 20px; }
.info { margin-bottom: 20px; }
.info-item { display: flex; margin-bottom: 8px; }
.info-item label { width: 60px; color: #999; font-size: 14px; }
.info-item span { font-size: 14px; color: #333; }
.actions { display: flex; gap: 12px; }
.action-btn { padding: 8px 24px; background: #fb7299; color: white; border: none; border-radius: 4px; cursor: pointer; }
.action-btn:hover { background: #e8648a; }
.msg { margin-top: 12px; color: #4caf50; font-size: 14px; }
.loading, .empty { text-align: center; color: #999; padding: 40px; }
</style>
