import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000
})

export function getPersonalizedRecommend(userId, limit = 20) {
  return api.get(`/recommend/personalized/${userId}`, { params: { limit } })
}

export function getHotRecommend(limit = 20) {
  return api.get('/recommend/hot', { params: { limit } })
}

export function getCategoryRecommend(category, limit = 20) {
  return api.get(`/recommend/category/${category}`, { params: { limit } })
}

export function getVideoDetail(id) {
  return api.get(`/videos/${id}`)
}

export function getHotVideos(limit = 10) {
  return api.get('/videos/hot', { params: { limit } })
}

export function getUserProfile(userId) {
  return api.get(`/users/${userId}/profile`)
}

export function recordBehavior(data) {
  return api.post('/behavior', data)
}

export default api
