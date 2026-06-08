import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/views/LoginView.vue'), meta: { guest: true } },
    { path: '/register', name: 'Register', component: () => import('@/views/RegisterView.vue'), meta: { guest: true } },
    { path: '/', name: 'Main', component: () => import('@/views/MainView.vue'), meta: { requiresAuth: true } },
    { path: '/settings', name: 'Settings', component: () => import('@/views/SettingsView.vue'), meta: { requiresAuth: true } },
  ],
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.token) next('/login')
  else if (to.meta.guest && auth.token) next('/')
  else next()
})

export default router
