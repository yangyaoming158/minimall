<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { type FormInstance, type FormRules } from 'element-plus'

// The dialog only collects the free-text adjustment; the view owns the
// requestId idempotency key (minted fresh per submit attempt) and the API call.
export interface AdjustFormPayload {
  delta: number
  reason: string
}

const props = defineProps<{
  modelValue: boolean
  productId?: string | null
  submitting?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submit', payload: AdjustFormPayload): void
}>()

const formRef = ref<FormInstance>()
const form = reactive<{ delta: number | null; reason: string }>({
  delta: null,
  reason: '',
})

const rules: FormRules = {
  delta: [{ required: true, message: '请输入调整数量', trigger: 'blur' }],
  reason: [
    { required: true, message: '请输入调整原因', trigger: 'blur' },
    { max: 512, message: '原因长度不能超过 512', trigger: 'blur' },
  ],
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return
    }
    form.delta = null
    form.reason = ''
    formRef.value?.clearValidate()
  },
)

function close(): void {
  emit('update:modelValue', false)
}

async function onConfirm(): Promise<void> {
  if (props.submitting) {
    return // in-flight guard: ignore re-clicks while the view is submitting
  }
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) {
    return
  }
  // Deterministic guards (happy-dom async-validator can pass empty fields):
  // delta must be a non-zero integer and reason must be non-blank.
  if (form.delta === null || form.delta === 0) {
    return
  }
  if (!form.reason.trim()) {
    return
  }
  emit('submit', { delta: form.delta, reason: form.reason.trim() })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`调整库存${productId ? ' · ' + productId : ''}`"
    width="460px"
    @update:model-value="close"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="调整数量" prop="delta">
        <el-input-number v-model="form.delta" :step="1" :precision="0" controls-position="right" />
        <span class="hint">正数入库 / 负数出库</span>
      </el-form-item>
      <el-form-item label="调整原因" prop="reason">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
          placeholder="必填，写入库存流水用于审计"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onConfirm">确定调整</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint {
  margin-left: var(--space-12);
  font-size: var(--text-xs);
  color: var(--text-faint);
}
</style>
