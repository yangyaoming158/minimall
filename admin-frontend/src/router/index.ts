import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/types/api'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/products' },
      {
        path: 'products',
        name: 'products',
        component: () => import('@/views/ProductsView.vue'),
        meta: { requiresAuth: true, title: '商品管理' },
      },
      {
        path: 'inventories',
        name: 'inventories',
        component: () => import('@/views/InventoriesView.vue'),
        meta: { requiresAuth: true, title: '库存管理' },
      },
      {
        path: 'ai-suggestions',
        name: 'ai-suggestions',
        component: () => import('@/views/AiSuggestionsView.vue'),
        meta: { requiresAuth: true, title: 'AI 建议审批' },
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/OrdersView.vue'),
        meta: { requiresAuth: true, title: '订单管理' },
      },
      {
        path: 'payments',
        name: 'payments',
        component: () => import('@/views/PaymentsView.vue'),
        meta: { requiresAuth: true, title: '支付管理' },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/NotificationsView.vue'),
        meta: { requiresAuth: true, title: '通知管理' },
      },
      {
        path: 'audit-logs',
        name: 'audit-logs',
        component: () => import('@/views/AuditLogsView.vue'),
        meta: { requiresAuth: true, title: '操作日志' },
      },
    ],
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth) {
    if (!auth.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    // Token survived a refresh but the in-memory admin is gone: verify identity.
    if (!auth.currentAdmin) {
      try {
        await auth.fetchCurrentAdmin()
      } catch (error) {
        // Non-admin token -> /api/admin/me returns 403 -> show the forbidden page.
        if (error instanceof ApiError && error.httpStatus === 403) {
          return { path: '/403' }
        }
        // 401 is already handled by the http interceptor (clears token +
        // redirects to /login); fall back to /login for any other failure.
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }
    return true
  }

  if (to.meta.guestOnly && auth.isLoggedIn) {
    // A confirmed admin in memory: skip the login page.
    if (auth.currentAdmin) {
      return { path: '/products' }
    }
    // Token present but identity unconfirmed (e.g. a residual non-admin USER
    // token from the customer storefront). Verify before bouncing away from
    // /login; otherwise the user is stuck in a /login -> /products -> 403 loop
    // and cannot sign in as an admin without manually clearing storage.
    try {
      await auth.fetchCurrentAdmin()
      return { path: '/products' }
    } catch {
      auth.logout()
      return true
    }
  }

  return true
})

export default router
