<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import Button from '@/components/atoms/Button.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    body?: string
    confirmLabel?: string
    cancelLabel?: string
    tone?: 'default' | 'danger'
  }>(),
  {
    body: '',
    confirmLabel: '确认',
    cancelLabel: '取消',
    tone: 'default',
  },
)

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const confirmBtn = ref<InstanceType<typeof Button> | null>(null)
let lastFocused: HTMLElement | null = null

function onConfirm(): void {
  emit('confirm')
}

function onCancel(): void {
  emit('cancel')
}

function onBackdropClick(): void {
  onCancel()
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    e.preventDefault()
    onCancel()
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      lastFocused = document.activeElement as HTMLElement | null
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onKeydown)
      await nextTick()
      const root = confirmBtn.value as unknown as { $el?: HTMLElement } | null
      root?.$el?.focus?.()
    } else {
      document.body.style.overflow = ''
      document.removeEventListener('keydown', onKeydown)
      lastFocused?.focus?.()
      lastFocused = null
    }
  },
)

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  document.removeEventListener('keydown', onKeydown)
})

const dialogClasses = computed(() => ['cm__dialog', `cm__dialog--${props.tone}`])
</script>

<template>
  <Teleport to="body">
    <Transition name="cm">
      <div v-if="open" class="cm" role="presentation">
        <div class="cm__backdrop" @click="onBackdropClick" />
        <div
          :class="dialogClasses"
          role="alertdialog"
          aria-modal="true"
          :aria-labelledby="`cm-title`"
          :aria-describedby="body ? `cm-body` : undefined"
        >
          <h2 id="cm-title" class="cm__title">{{ title }}</h2>
          <p v-if="body" id="cm-body" class="cm__body">{{ body }}</p>
          <div class="cm__actions">
            <Button variant="ghost" size="md" @click="onCancel">
              {{ cancelLabel }}
            </Button>
            <Button
              ref="confirmBtn"
              variant="primary"
              size="md"
              @click="onConfirm"
            >
              {{ confirmLabel }}
            </Button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.cm {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.cm__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 17, 21, 0.45);
}

.cm__dialog {
  position: relative;
  width: 100%;
  max-width: 420px;
  background: var(--surface);
  border: 1px solid var(--ink-100);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow:
    0 1px 2px rgba(15, 17, 21, 0.05),
    0 12px 32px rgba(15, 17, 21, 0.18);
  font-family: var(--font-sans);
}

.cm__title {
  margin: 0 0 8px;
  font-size: var(--t-h2-size);
  line-height: var(--t-h2-lh);
  font-weight: var(--t-h2-weight);
  color: var(--ink-900);
}

.cm__body {
  margin: 0 0 24px;
  font-size: var(--t-body-size);
  line-height: var(--t-body-lh);
  color: var(--ink-500);
}

.cm__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.cm-enter-active,
.cm-leave-active {
  transition: opacity var(--dur-2) var(--ease);
}

.cm-enter-active .cm__dialog,
.cm-leave-active .cm__dialog {
  transition:
    opacity var(--dur-2) var(--ease),
    transform var(--dur-2) var(--ease);
}

.cm-enter-from,
.cm-leave-to {
  opacity: 0;
}

.cm-enter-from .cm__dialog,
.cm-leave-to .cm__dialog {
  opacity: 0;
  transform: scale(0.96);
}

@media (prefers-reduced-motion: reduce) {
  .cm-enter-active,
  .cm-leave-active,
  .cm-enter-active .cm__dialog,
  .cm-leave-active .cm__dialog {
    transition: none;
  }
}
</style>
