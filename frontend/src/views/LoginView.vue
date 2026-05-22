<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/types/api'
import type { LoginRequest } from '@/types/user'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<LoginRequest>({
  username: '',
  password: '',
})

const rules: FormRules<LoginRequest> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// Only honour an internal, single-slash path to avoid open-redirect to
// external sites or protocol-relative URLs.
function safeRedirect(): string {
  const target = route.query.redirect
  if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) {
    return target
  }
  return '/products'
}

// Carry the redirect query across to the register page so the round-trip
// still lands the user back where they started.
function toRegister() {
  const redirect = route.query.redirect
  router.push({ path: '/register', query: typeof redirect === 'string' ? { redirect } : {} })
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await auth.login({ ...form })
    ElMessage.success('登录成功')
    router.push(safeRedirect())
  } catch (err) {
    // Network / 401 / 429 / 500 already surfaced by the http interceptor.
    // Show business messages (e.g. wrong credentials) inline without crashing.
    if (err instanceof ApiError) {
      ElMessage.error(err.message || '登录失败，请稍后再试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">登录</h1>
      <p class="auth-subtitle">欢迎回到 MiniMall</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="form.username"
            placeholder="请输入用户名"
            autocomplete="username"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="submitting"
            @click="onSubmit"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        还没有账号？
        <el-link type="primary" :underline="false" @click="toRegister">立即注册</el-link>
      </div>
    </div>
  </section>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding: 48px 16px;
}

.auth-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 40px 36px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

.auth-title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 600;
  color: #1f2329;
}

.auth-subtitle {
  margin: 0 0 28px;
  color: #909399;
  font-size: 14px;
}

.submit-btn {
  width: 100%;
}

.auth-footer {
  margin-top: 8px;
  text-align: center;
  color: #606266;
  font-size: 14px;
}
</style>
