import api from './api'
import type { AuthResponse, UserInfo } from '@/types'

export const authService = {
  login(username: string, password: string) {
    return api.post<unknown, { data: AuthResponse }>('/auth/login', { username, password })
  },
  register(username: string, email: string, password: string) {
    return api.post<unknown, { data: AuthResponse }>('/auth/register', { username, email, password })
  },
  getMe() {
    return api.get<unknown, { data: UserInfo }>('/auth/me')
  },
}
