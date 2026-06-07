<template>
  <div>
    <h2>我的画像</h2>
    <div class="user-bar">
      <label>用户ID：</label>
      <input v-model.number="userId" type="number" />
      <button @click="load">查询</button>
    </div>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="!profile" class="empty">暂无画像数据</div>
    <div v-else class="profile-card">
      <div class="profile-header">用户 #{{ profile.userId }}</div>
      <div class="profile-body">
        <div class="stat"><label>活跃等级</label><span class="level">{{ profile.activeLevel }}</span></div>
        <div class="stat"><label>累计观看</label><span>{{ profile.totalWatchCount }} 次</span></div>
        <div class="stat"><label>平均观看</label><span>{{ formatTime(profile.avgViewingTime) }}</span></div>
        <div class="stat"><label>点赞率</label><span>{{ (profile.likeRate * 100).toFixed(1) }}%</span></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getUserProfile } from '../api/index.js'

const route = useRoute()
const userId = ref(parseInt(route.params.userId) || 85500)
const profile = ref(null)
const loading = ref(false)

function formatTime(seconds) {
  if (!seconds) return '0秒'
  const min = Math.floor(seconds / 60)
  const sec = Math.floor(seconds % 60)
  return min > 0 ? `${min}分${sec}秒` : `${sec}秒`
}

async function load() {
  loading.value = true
  try {
    const res = await getUserProfile(userId.value)
    profile.value = res.data
  } catch {
    profile.value = null
  }
  loading.value = false
}

onMounted(load)
</script>

<style scoped>
h2 { color: #333; font-size: 20px; margin-bottom: 16px; }
.user-bar { margin-bottom: 20px; display: flex; align-items: center; gap: 8px; }
.user-bar input { width: 100px; padding: 6px 10px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }
.user-bar button { padding: 6px 16px; background: #fb7299; color: white; border: none; border-radius: 4px; cursor: pointer; }
.profile-card { background: white; border-radius: 8px; overflow: hidden; }
.profile-header { background: #fb7299; color: white; padding: 16px 20px; font-size: 16px; font-weight: bold; }
.profile-body { padding: 20px; }
.stat { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.stat:last-child { border-bottom: none; }
.stat label { color: #999; font-size: 14px; }
.stat span { font-size: 14px; color: #333; font-weight: bold; }
.level { color: #fb7299; font-size: 18px; }
.loading, .empty { text-align: center; color: #999; padding: 40px; }
</style>
