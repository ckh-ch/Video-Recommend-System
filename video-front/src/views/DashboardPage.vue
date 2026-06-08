<template>
  <div class="dashboard">
    <div class="header">
      <div class="header-left">
        <h1 class="title">短视频推荐系统 · 数据大屏</h1>
        <span class="subtitle">Video Recommendation System Dashboard</span>
      </div>
      <div class="header-right">
        <div class="mode-tabs">
          <button :class="['mode-tab', { active: mode === 'global' }]" @click="switchMode('global')">全局数据</button>
          <button :class="['mode-tab', { active: mode === 'user' }]" @click="switchMode('user')">用户画像</button>
        </div>
        <div v-if="mode === 'user'" class="user-switcher">
          <span class="switcher-label">用户ID：</span>
          <input v-model.number="inputUserId" type="number" class="switcher-input" placeholder="输入用户ID" @keyup.enter="switchUser" />
          <button @click="switchUser" class="switcher-btn">切换</button>
        </div>
        <div class="time">{{ currentTime }}</div>
      </div>
    </div>

    <div class="content">
      <DashboardSkeleton v-if="loading" />
      <template v-else>
        <!-- ========== 全局模式 ========== -->
        <template v-if="mode === 'global'">
          <KpiCards :summary="summary" />

          <div class="row-3c">
            <div class="col"><CategoryRoseChart :data="categoryDist" /></div>
            <div class="col"><InteractionBarChart :data="behaviorStats" /></div>
            <div class="col"><ActivityPieChart :data="activityDist" /></div>
          </div>

          <div class="row-full">
            <AvgViewTimeChart :data="behaviorStats" />
          </div>

          <div class="row-full">
            <HotVideos />
          </div>

          <div class="row-3c">
            <div class="col"><HourlyTrendChart :data="hourlyTrend" /></div>
            <div class="col"><RealtimeActions /></div>
            <div class="col"><OverviewRingChart /></div>
          </div>
        </template>

        <!-- ========== 用户模式 ========== -->
        <template v-if="mode === 'user'">
          <UserKpiCards :summary="userSummary" />

          <div class="row-3c">
            <div class="col"><CategoryRoseChart :data="userCategoryDist" /></div>
            <div class="col"><InteractionBarChart :data="userBehaviorStats" /></div>
            <div class="col"><ActivityPieChart :data="activityDist" /></div>
          </div>

          <div class="row-full">
            <AvgViewTimeChart :data="userBehaviorStats" />
          </div>

          <div class="row-2c">
            <div class="col"><InterestTags :userId="currentUserId" /></div>
            <div class="col col-wide"><RecommendVideos :userId="currentUserId" /></div>
          </div>

          <div class="row-3c">
            <div class="col"><HourlyTrendChart :data="userHourlyTrend" /></div>
            <div class="col"><RealtimeActions :userId="currentUserId" /></div>
            <div class="col"><OverviewRingChart /></div>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import api from '../api/index.js'
import KpiCards from '../components/dashboard/KpiCards.vue'
import UserKpiCards from '../components/dashboard/UserKpiCards.vue'
import CategoryRoseChart from '../components/dashboard/CategoryRoseChart.vue'
import InteractionBarChart from '../components/dashboard/InteractionBarChart.vue'
import ActivityPieChart from '../components/dashboard/ActivityPieChart.vue'
import AvgViewTimeChart from '../components/dashboard/AvgViewTimeChart.vue'
import HourlyTrendChart from '../components/dashboard/HourlyTrendChart.vue'
import RealtimeActions from '../components/dashboard/RealtimeActions.vue'
import OverviewRingChart from '../components/dashboard/OverviewRingChart.vue'
import InterestTags from '../components/dashboard/InterestTags.vue'
import RecommendVideos from '../components/dashboard/RecommendVideos.vue'
import HotVideos from '../components/dashboard/HotVideos.vue'
import DashboardSkeleton from '../components/dashboard/DashboardSkeleton.vue'

// mode - 持久化到 localStorage，刷新不丢失
const savedMode = localStorage.getItem('dashboard_mode')
const mode = ref(savedMode === 'user' ? 'user' : 'global')

function switchMode(m) {
  mode.value = m
  localStorage.setItem('dashboard_mode', m)
  fetchData()
}

// global data
const summary = ref({})
const categoryDist = ref([])
const activityDist = ref([])
const behaviorStats = ref([])
const hourlyTrend = ref([])

// user data
const userSummary = ref({})
const userCategoryDist = ref([])
const userBehaviorStats = ref([])
const userHourlyTrend = ref([])

// shared
const currentTime = ref('')
const savedUserId = localStorage.getItem('dashboard_userId')
const defaultUserId = savedUserId ? Number(savedUserId) : 85500
const inputUserId = ref(defaultUserId)
const currentUserId = ref(defaultUserId)
const loading = ref(true)
let timeTimer = null

function switchUser() {
  currentUserId.value = inputUserId.value
  localStorage.setItem('dashboard_userId', String(inputUserId.value))
  if (mode.value === 'user') fetchUserData(currentUserId.value)
}

async function fetchData() {
  loading.value = true
  try {
    if (mode.value === 'global') {
      await fetchGlobalData()
    } else {
      await Promise.all([fetchGlobalData(), fetchUserData(currentUserId.value)])
    }
  } catch (e) {
    console.error('Dashboard data fetch failed:', e)
  }
  loading.value = false
}

async function fetchGlobalData() {
  const [s, c, a, b, h] = await Promise.all([
    api.get('/dashboard/summary'),
    api.get('/dashboard/category-dist'),
    api.get('/dashboard/activity-dist'),
    api.get('/dashboard/behavior-stats'),
    api.get('/dashboard/hourly-trend')
  ])
  summary.value = s.data
  categoryDist.value = c.data
  activityDist.value = a.data
  behaviorStats.value = b.data
  hourlyTrend.value = h.data
}

async function fetchUserData(uid) {
  const [us, uc, ub, uh] = await Promise.all([
    api.get(`/dashboard/user/${uid}/summary`),
    api.get(`/dashboard/user/${uid}/category-dist`),
    api.get(`/dashboard/user/${uid}/behavior-stats`),
    api.get(`/dashboard/user/${uid}/hourly-trend`)
  ])
  userSummary.value = us.data
  userCategoryDist.value = uc.data
  userBehaviorStats.value = ub.data
  userHourlyTrend.value = uh.data
}

function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  fetchData()
  updateTime()
  timeTimer = setInterval(updateTime, 1000)
})

onBeforeUnmount(() => clearInterval(timeTimer))
</script>

<style>
body { margin: 0; background: #020617; }
</style>

<style scoped>
.dashboard {
  width: 100%; min-height: 100vh; background: #020617; padding: 20px 24px;
  font-family: 'Fira Sans', -apple-system, BlinkMacSystemFont, sans-serif;
  box-sizing: border-box;
}

.header {
  display: flex; justify-content: space-between; align-items: flex-end;
  margin-bottom: 20px; flex-wrap: wrap; gap: 10px;
}
.title { font-size: 24px; font-weight: 700; color: #F8FAFC; margin: 0; letter-spacing: 1px; }
.subtitle { font-size: 12px; color: #64748B; letter-spacing: 2px; text-transform: uppercase; margin-top: 2px; }
.time { font-family: 'Fira Code', monospace; font-size: 18px; color: #94A3B8; }

.header-right { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }

/* mode tabs */
.mode-tabs { display: flex; gap: 2px; background: rgba(30,41,59,.6); border-radius: 8px; padding: 3px; }
.mode-tab {
  padding: 6px 16px; border: none; border-radius: 6px; cursor: pointer;
  font-size: 13px; font-weight: 500; color: #94A3B8; background: transparent;
  transition: all .2s;
}
.mode-tab.active { background: #3B82F6; color: #F8FAFC; box-shadow: 0 2px 8px rgba(59,130,246,.3); }
.mode-tab:hover:not(.active) { color: #CBD5E1; }

/* user switcher */
.user-switcher { display: flex; align-items: center; gap: 6px; }
.switcher-label { font-size: 12px; color: #94A3B8; white-space: nowrap; }
.switcher-input {
  width: 90px; padding: 4px 8px; border: 1px solid #1E293B; border-radius: 4px;
  background: rgba(30,41,59,.5); color: #F8FAFC; font-size: 13px; outline: none;
}
.switcher-input:focus { border-color: #3B82F6; }
.switcher-btn {
  padding: 4px 12px; background: #3B82F6; color: white; border: none; border-radius: 4px;
  cursor: pointer; font-size: 12px;
}
.switcher-btn:hover { background: #2563EB; }

.content { display: flex; flex-direction: column; gap: 14px; }

.row-3c { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 14px; }
.row-2c { display: grid; grid-template-columns: 1fr 2fr; gap: 14px; align-items: stretch; }
.row-full { display: grid; grid-template-columns: 1fr; }

.col { min-width: 0; }
.col-wide { min-width: 0; }

@media (min-width: 1600px) {
  .dashboard { padding: 24px 40px; }
  .content { gap: 16px; }
  .row-3c, .row-2c { gap: 16px; }
}
</style>
