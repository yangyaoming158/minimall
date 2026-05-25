<script setup lang="ts">
import ConfirmModal from '@/components/atoms/ConfirmModal.vue'
import { useConfirmState } from '@/composables/useConfirm'

const { state, accept, dismiss } = useConfirmState()
</script>

<template>
  <ConfirmModal
    :open="!!state"
    :title="state?.title ?? ''"
    :body="state?.body"
    :confirm-label="state?.confirmLabel"
    :cancel-label="state?.cancelLabel"
    :tone="state?.tone"
    @confirm="accept"
    @cancel="dismiss"
  />
</template>
