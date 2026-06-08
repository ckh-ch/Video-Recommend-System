import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000
})

export function get(url, params) {
  return api.get(url, { params })
}

export default api
