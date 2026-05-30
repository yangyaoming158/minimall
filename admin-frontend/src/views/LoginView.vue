<script setup lang="ts">
// Placeholder login screen. The functional login form (validation, submit,
// error handling, redirect-on-success) is implemented in Task 11; this step
// only establishes the project, routing, layout, guards, and API client.
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="brand-mark">◆</span>
        <span class="brand-text">MiniMall<small>Admin</small></span>
      </div>
      <h1 class="title">管理员登录</h1>
      <p class="note">登录表单将在 Task 11 接入（POST /api/admin/login）。</p>
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
  text-align: center;
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
  margin: var(--space-24) 0 var(--space-8);
  font-size: var(--text-xl);
  color: var(--text-strong);
}

.note {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}
</style>
