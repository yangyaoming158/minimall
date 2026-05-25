<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthHero from '@/components/AuthHero.vue'
import Button from '@/components/atoms/Button.vue'
import Field from '@/components/atoms/Field.vue'
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

type FieldKey = keyof LoginRequest
const errors = reactive<Record<FieldKey, string>>({
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
function toRegister(): void {
  const redirect = route.query.redirect
  router.push({ path: '/register', query: typeof redirect === 'string' ? { redirect } : {} })
}

function clearFieldError(key: FieldKey): void {
  if (errors[key]) errors[key] = ''
}

function validate(): Promise<boolean> {
  return new Promise((resolve) => {
    if (!formRef.value) {
      resolve(false)
      return
    }
    formRef.value.validate((valid, fields) => {
      ;(Object.keys(errors) as FieldKey[]).forEach((k) => {
        errors[k] = ''
      })
      if (!valid && fields) {
        for (const [key, errs] of Object.entries(fields)) {
          const list = errs as Array<{ message?: string }>
          errors[key as FieldKey] = list[0]?.message ?? '校验失败'
        }
      }
      resolve(Boolean(valid))
    })
  })
}

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    const valid = await validate()
    if (!valid) return

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
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth">
    <AuthHero
      class="auth__hero"
      headline="Welcome back."
      subtitle="Sign in to continue shopping at MiniMall."
    />

    <div class="auth__panel">
      <div class="auth__form">
        <h1 class="auth__title">登录</h1>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          @submit.prevent="onSubmit"
        >
          <el-form-item prop="username" :show-message="false" class="auth__row">
            <Field label="用户名" :error="errors.username">
              <input
                v-model.trim="form.username"
                type="text"
                class="auth-input"
                autocomplete="username"
                placeholder="请输入用户名"
                @input="clearFieldError('username')"
              />
            </Field>
          </el-form-item>

          <el-form-item prop="password" :show-message="false" class="auth__row">
            <Field label="密码" :error="errors.password">
              <input
                v-model="form.password"
                type="password"
                class="auth-input"
                autocomplete="current-password"
                placeholder="请输入密码"
                @input="clearFieldError('password')"
                @keyup.enter="onSubmit"
              />
            </Field>
          </el-form-item>

          <Button
            variant="primary"
            size="lg"
            full
            :loading="submitting"
            type="submit"
            class="auth__submit"
          >
            登录
          </Button>
        </el-form>

        <p class="auth__footer">
          还没有账号？
          <button type="button" class="text-link" @click="toRegister">立即注册</button>
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 55% 45%;
  min-height: 640px;
  margin: -32px -24px;
  background: var(--surface);
}

.auth__hero {
  height: 100%;
}

.auth__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: var(--surface);
}

.auth__form {
  width: 100%;
  max-width: 420px;
}

.auth__title {
  margin: 0 0 28px;
  font-family: var(--font-sans);
  font-size: var(--t-h1-size);
  line-height: var(--t-h1-lh);
  font-weight: var(--t-h1-weight);
  color: var(--ink-900);
}

.auth__row {
  margin-bottom: 18px;
}

.auth__submit {
  margin-top: 4px;
}

.auth__footer {
  margin: 20px 0 0;
  font-family: var(--font-sans);
  font-size: var(--t-caption-size);
  line-height: var(--t-caption-lh);
  color: var(--ink-500);
  text-align: center;
}

.text-link {
  background: none;
  border: none;
  padding: 0;
  color: var(--ink-900);
  font: inherit;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
  text-decoration-thickness: 1px;
  transition: color var(--dur-2) var(--ease);
}

.text-link:hover {
  color: var(--accent-terracotta);
}

.auth-input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 400;
  line-height: 1.4;
  color: var(--ink-900);
  background: var(--surface);
  border: 1px solid var(--ink-100);
  border-radius: var(--radius);
  outline: none;
  transition: border-color var(--dur-2) var(--ease);
}

.auth-input::placeholder {
  color: var(--ink-300);
}

.auth-input:focus {
  border-color: var(--ink-700);
}

.auth-input:disabled {
  background: var(--ink-100);
  cursor: not-allowed;
}

/* Element Plus form-item resets — we render our own structure inside. */
.auth__row :deep(.el-form-item__content) {
  line-height: 1.4;
}

.auth__row :deep(.el-form-item__error) {
  display: none;
}

@media (max-width: 899px) {
  .auth {
    grid-template-columns: 1fr;
    grid-template-rows: 120px 1fr;
    min-height: 0;
  }

  .auth__panel {
    padding: 32px 24px 48px;
  }
}

@media (max-width: 639px) {
  .auth {
    margin: -24px -16px;
  }

  .auth__panel {
    padding: 24px 16px 40px;
  }
}
</style>
