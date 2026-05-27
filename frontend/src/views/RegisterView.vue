<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthHero from '@/components/AuthHero.vue'
import Button from '@/components/atoms/Button.vue'
import Field from '@/components/atoms/Field.vue'
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

type FieldKey = keyof RegisterForm
const errors = reactive<Record<FieldKey, string>>({
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

function toLogin(): void {
  const redirect = route.query.redirect
  router.push({ path: '/login', query: typeof redirect === 'string' ? { redirect } : {} })
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
      headline="Create your MiniMall account."
      subtitle="Takes less than a minute."
      reel-seed="register"
    />

    <div class="auth__panel">
      <div class="auth__form">
        <h1 class="auth__title">注册</h1>

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
                autocomplete="new-password"
                placeholder="6-128 个字符"
                @input="clearFieldError('password')"
              />
            </Field>
          </el-form-item>

          <el-form-item prop="confirmPassword" :show-message="false" class="auth__row">
            <Field label="确认密码" :error="errors.confirmPassword">
              <input
                v-model="form.confirmPassword"
                type="password"
                class="auth-input"
                autocomplete="new-password"
                placeholder="请再次输入密码"
                @input="clearFieldError('confirmPassword')"
                @keyup.enter="onSubmit"
              />
            </Field>
          </el-form-item>

          <el-form-item prop="email" :show-message="false" class="auth__row">
            <Field label="邮箱（选填）" :error="errors.email">
              <input
                v-model.trim="form.email"
                type="email"
                class="auth-input"
                autocomplete="email"
                placeholder="可选"
                @input="clearFieldError('email')"
              />
            </Field>
          </el-form-item>

          <el-form-item prop="phone" :show-message="false" class="auth__row">
            <Field label="手机号（选填）" :error="errors.phone">
              <input
                v-model.trim="form.phone"
                type="tel"
                class="auth-input"
                autocomplete="tel"
                placeholder="可选"
                @input="clearFieldError('phone')"
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
            注册
          </Button>
        </el-form>

        <p class="auth__demo">演示账号，不会发送短信或邮件验证。</p>

        <p class="auth__footer">
          已有账号？
          <button type="button" class="text-link" @click="toLogin">去登录</button>
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 55% 45%;
  min-height: 720px;
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
  margin: 0 0 24px;
  font-family: var(--font-sans);
  font-size: var(--t-h1-size);
  line-height: var(--t-h1-lh);
  font-weight: var(--t-h1-weight);
  color: var(--ink-900);
}

.auth__row {
  margin-bottom: 16px;
}

.auth__submit {
  margin-top: 4px;
}

.auth__demo {
  margin: 16px 0 0;
  font-family: var(--font-sans);
  font-size: var(--t-caption-size);
  line-height: var(--t-caption-lh);
  color: var(--ink-500);
  text-align: center;
}

.auth__footer {
  margin: 12px 0 0;
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
