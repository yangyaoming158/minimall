<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

interface NavItem {
  index: string
  label: string
}

const navItems: NavItem[] = [
  { index: '/products', label: '商品管理' },
  { index: '/inventories', label: '库存管理' },
  { index: '/orders', label: '订单管理' },
  { index: '/payments', label: '支付管理' },
  { index: '/notifications', label: '通知管理' },
  { index: '/audit-logs', label: '操作日志' },
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { currentAdmin } = storeToRefs(auth)

const activeIndex = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string | undefined) ?? '')
const adminName = computed(() => currentAdmin.value?.username ?? '管理员')

function onCommand(command: string): void {
  if (command === 'logout') {
    auth.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">◆</span>
        <span class="brand-text">MiniMall<small>Admin</small></span>
      </div>
      <el-menu
        :default-active="activeIndex"
        router
        class="nav"
        background-color="transparent"
        text-color="var(--sidebar-fg)"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in navItems" :key="item.index" :index="item.index">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="main">
      <header class="topbar">
        <h1 class="page-title">{{ pageTitle }}</h1>
        <el-dropdown trigger="click" @command="onCommand">
          <span class="admin-chip">
            {{ adminName }}
            <el-icon class="caret"><svg viewBox="0 0 12 12" width="10" height="10"><path d="M2 4l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.5" /></svg></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: var(--sidebar-w);
  flex-shrink: 0;
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
}

.brand {
  height: var(--topbar-h);
  display: flex;
  align-items: center;
  gap: var(--space-8);
  padding: 0 var(--space-20);
  color: #fff;
  font-weight: 600;
}

.brand-mark {
  color: var(--accent);
  font-size: var(--text-lg);
}

.brand-text small {
  margin-left: var(--space-8);
  color: var(--sidebar-fg-muted);
  font-weight: 500;
  font-size: var(--text-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.nav {
  border-right: none;
  padding: var(--space-8) var(--space-12);
}

.nav :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  border-radius: var(--radius);
  margin-bottom: var(--space-4);
}

.nav :deep(.el-menu-item:hover) {
  background: var(--sidebar-bg-hover);
}

.nav :deep(.el-menu-item.is-active) {
  background: var(--sidebar-active-bg);
  font-weight: 600;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: var(--topbar-h);
  flex-shrink: 0;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-24);
}

.page-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text-strong);
}

.admin-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-8);
  cursor: pointer;
  color: var(--text);
  font-weight: 500;
  outline: none;
}

.caret {
  color: var(--text-faint);
}

.content {
  flex: 1;
  padding: var(--space-24);
  overflow: auto;
}
</style>
