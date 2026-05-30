<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const loading = ref(false)
const errorMessage = ref('')

// Only follow same-origin internal redirects, never an absolute/external URL.
function safeRedirect(): string {
  const target = route.query.redirect
  if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) {
    return target
  }
  return '/products'
}

async function onSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    await auth.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    router.push(safeRedirect())
  } catch (error) {
    // USER accounts are rejected with 403 by /api/admin/login; bad credentials
    // come back as 401. Surface a clear, admin-specific message either way.
    if (error instanceof ApiError && error.httpStatus === 403) {
      errorMessage.value = '该账号没有管理员权限，请使用管理员账号登录'
    } else if (error instanceof ApiError) {
      errorMessage.value = error.message || '登录失败，请稍后重试'
    } else {
      errorMessage.value = '登录失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="brand-mark">◆</span>
        <span class="brand-text">MiniMall<small>Admin</small></span>
      </div>
      <h1 class="title">管理员登录</h1>
      <p class="subtitle">请输入管理员账号以进入运营后台</p>

      <el-alert
        v-if="errorMessage"
        class="login-error"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="管理员用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            autocomplete="current-password"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button
          class="submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="onSubmit"
        >
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--canvas);
  padding: var(--space-24);
}

.login-card {
  width: 100%;
  max-width: 380px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-2);
  padding: var(--space-32);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-8);
  font-weight: 600;
  color: var(--text-strong);
}

.brand-mark {
  color: var(--accent);
  font-size: var(--text-xl);
}

.brand-text small {
  margin-left: var(--space-8);
  color: var(--text-faint);
  font-weight: 500;
  font-size: var(--text-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.title {
  margin: var(--space-24) 0 var(--space-4);
  font-size: var(--text-xl);
  color: var(--text-strong);
}

.subtitle {
  margin: 0 0 var(--space-20);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.login-error {
  margin-bottom: var(--space-16);
}

.submit {
  width: 100%;
  margin-top: var(--space-8);
}
</style>
