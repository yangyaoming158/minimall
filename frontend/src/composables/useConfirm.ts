import { ref } from 'vue'

export interface ConfirmOptions {
  title: string
  body?: string
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'default' | 'danger'
}

interface ConfirmState extends ConfirmOptions {
  resolver: (value: boolean) => void
}

const state = ref<ConfirmState | null>(null)

function present(opts: ConfirmOptions): Promise<boolean> {
  // Reject any already-open dialog so call-site Promises always resolve.
  if (state.value) {
    state.value.resolver(false)
  }
  return new Promise((resolve) => {
    state.value = { ...opts, resolver: resolve }
  })
}

function settle(value: boolean): void {
  const current = state.value
  if (!current) return
  state.value = null
  current.resolver(value)
}

export function useConfirm(): (opts: ConfirmOptions) => Promise<boolean> {
  return present
}

export function useConfirmState() {
  return {
    state,
    accept: () => settle(true),
    dismiss: () => settle(false),
  }
}
