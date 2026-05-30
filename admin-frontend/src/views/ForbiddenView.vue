<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

// The current token is not an admin session; clear it and let the user sign in
// with an admin account.
function backToLogin(): void {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="center-page">
    <el-result icon="warning" title="403" sub-title="无权限访问管理后台，请使用管理员账号登录。">
      <template #extra>
        <el-button type="primary" @click="backToLogin">返回登录</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.center-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--canvas);
}
</style>
