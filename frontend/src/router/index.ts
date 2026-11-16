import type { Component } from 'vue'
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataBoard, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    /** No token required. */
    public?: boolean
    title?: string
    /** Roles allowed to open the page; empty or absent means any signed-in user. */
    roles?: string[]
    /** Sidebar icon, mirrored from the route so the menu has one source of truth. */
    icon?: Component
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: 'Sign in' }
  },
  {
    path: '/',
    component: DefaultLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: 'Dashboard', icon: DataBoard, roles: ['ROLE_ADMIN', 'ROLE_USER'] }
      },
      {
        path: 'employees',
        name: 'employees',
        component: () => import('@/views/EmployeeListView.vue'),
        meta: { title: 'Employees', icon: User, roles: ['ROLE_ADMIN', 'ROLE_USER'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true, title: 'Page not found' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'dashboard' }
  }

  // Route-level RBAC: the guard runs before the page component is even loaded,
  // so a role-less user never downloads the admin bundle.
  if (!auth.hasAnyRole(to.meta.roles)) {
    ElMessage.warning('Your account does not have permission to open that page.')
    return to.name === 'dashboard' ? false : { name: 'dashboard' }
  }

  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : 'Employee Management'
  document.title = `${title} · Employee Management`
})

export default router
