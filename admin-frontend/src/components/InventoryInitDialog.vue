<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { type FormInstance, type FormRules } from 'element-plus'
import type { InitializeInventoryRequest } from '@/types/inventory'

const props = defineProps<{
  modelValue: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submit', payload: InitializeInventoryRequest): void
}>()

const formRef = ref<FormInstance>()
const form = reactive<InitializeInventoryRequest>({
  productId: '',
  initialStock: 0,
  safetyStock: 0,
})

const rules: FormRules = {
  productId: [
    { required: true, message: '请输入商品 ID', trigger: 'blur' },
    { max: 64, message: '商品 ID 长度不能超过 64', trigger: 'blur' },
  ],
  initialStock: [{ required: true, message: '请输入初始库存', trigger: 'blur' }],
  safetyStock: [{ required: true, message: '请输入安全库存', trigger: 'blur' }],
}

// Reset on each open so a previous attempt never leaks into a new one.
watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return
    }
    form.productId = ''
    form.initialStock = 0
    form.safetyStock = 0
    formRef.value?.clearValidate()
  },
)

function close(): void {
  emit('update:modelValue', false)
}

async function onConfirm(): Promise<void> {
  if (props.submitting) {
    return
  }
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) {
    return
  }
  // Deterministic guard: happy-dom's async-validator can pass an empty required
  // field, so re-check the free-text id before emitting.
  if (!form.productId.trim()) {
    return
  }
  emit('submit', {
    productId: form.productId.trim(),
    initialStock: form.initialStock,
    safetyStock: form.safetyStock,
  })
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="初始化库存" width="460px" @update:model-value="close">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="商品 ID" prop="productId">
        <el-input v-model="form.productId" placeholder="待初始化的商品编号" />
      </el-form-item>
      <el-form-item label="初始库存" prop="initialStock">
        <el-input-number v-model="form.initialStock" :min="0" :step="1" :precision="0" />
      </el-form-item>
      <el-form-item label="安全库存" prop="safetyStock">
        <el-input-number v-model="form.safetyStock" :min="0" :step="1" :precision="0" />
        <span class="hint">可用库存低于该阈值时标记为低库存</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onConfirm">初始化</el-button>
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
