<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/types/api'
import type { RegisterRequest } from '@/types/user'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

interface RegisterForm extends RegisterRequest {
  confirmPassword: string
}

const form = reactive<RegisterForm>({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
})

const validateConfirm = (_rule: unknown, value: string, callback: (e?: Error) => void) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules<RegisterForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度为 3-32 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 128, message: '密码长度为 6-128 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
  // email / phone are optional; only validate format when provided.
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^1\d{10}$/, message: '请输入有效的手机号', trigger: 'blur' }],
}

function toLogin() {
  const redirect = route.query.redirect
  router.push({ path: '/login', query: typeof redirect === 'string' ? { redirect } : {} })
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // Drop empty optional fields so we don't send blank email/phone.
    const payload: RegisterRequest = {
      username: form.username,
      password: form.password,
    }
    if (form.email) payload.email = form.email
    if (form.phone) payload.phone = form.phone

    await auth.register(payload)
    ElMessage.success('注册成功，请登录')
    toLogin()
  } catch (err) {
    // Network / 429 / 500 already surfaced by the http interceptor.
    // Show business messages (e.g. username taken) inline without crashing.
    if (err instanceof ApiError) {
      ElMessage.error(err.message || '注册失败，请稍后再试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">注册</h1>
      <p class="auth-subtitle">创建你的 MiniMall 账号</p>

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
            placeholder="6-128 个字符"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            autocomplete="new-password"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-form-item label="邮箱（选填）" prop="email">
          <el-input v-model.trim="form.email" placeholder="可选" clearable />
        </el-form-item>

        <el-form-item label="手机号（选填）" prop="phone">
          <el-input v-model.trim="form.phone" placeholder="可选" clearable />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="submitting"
            @click="onSubmit"
          >
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        已有账号？
        <el-link type="primary" :underline="false" @click="toLogin">去登录</el-link>
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
