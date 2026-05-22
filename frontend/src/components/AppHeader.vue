<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

function onLogout(): void {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <RouterLink class="brand" to="/products">MiniMall</RouterLink>
      <nav class="nav">
        <RouterLink class="nav__link" to="/products">商品</RouterLink>
        <template v-if="auth.isLoggedIn">
          <RouterLink class="nav__link" to="/orders">我的订单</RouterLink>
          <span class="nav__user">{{ auth.currentUser?.username ?? '' }}</span>
          <el-button text type="primary" @click="onLogout">退出</el-button>
        </template>
        <template v-else>
          <RouterLink class="nav__link" to="/login">登录</RouterLink>
          <RouterLink class="nav__link" to="/register">注册</RouterLink>
        </template>
      </nav>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;
}

.app-header__inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 60px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  font-size: 20px;
  font-weight: 700;
  color: #2563eb;
  text-decoration: none;
}

.nav {
  display: flex;
  align-items: center;
  gap: 20px;
}

.nav__link {
  color: #1f2329;
  text-decoration: none;
  font-size: 14px;
}

.nav__link.router-link-active {
  color: #2563eb;
  font-weight: 600;
}

.nav__user {
  color: #909399;
  font-size: 14px;
}
</style>
