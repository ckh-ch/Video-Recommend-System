import { createRouter, createWebHistory } from 'vue-router'
import RecommendPage from '../views/RecommendPage.vue'
import HotPage from '../views/HotPage.vue'
import VideoDetail from '../views/VideoDetail.vue'
import UserProfile from '../views/UserProfile.vue'

const routes = [
  { path: '/', redirect: '/recommend/85500' },
  { path: '/recommend/:userId', component: RecommendPage },
  { path: '/hot', component: HotPage },
  { path: '/video/:id', component: VideoDetail },
  { path: '/profile/:userId', component: UserProfile }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
