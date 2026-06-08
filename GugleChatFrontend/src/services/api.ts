import axios from 'axios'
import type { ApiResponse } from '@/types'

const api = axios.create({ baseURL: '/api', timeout: 15000, headers: { 'Content-Type': 'application/json' } })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code !== 200) return Promise.reject(new Error(body.message || 'Request failed'))
    return { ...response, data: body.data }
  },
  (error) => {
    if (error.response?.status === 401) { localStorage.removeItem('token'); window.location.href = '/login' }
    return Promise.reject(error)
  }
)

export default api
