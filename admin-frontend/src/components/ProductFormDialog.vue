<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { type FormInstance, type FormRules } from 'element-plus'
import type { AdminProduct } from '@/types/product'

export interface ProductFormPayload {
  productId: string
  name: string
  description: string
  imageUrl: string
  price: number
}

const props = defineProps<{
  modelValue: boolean
  /** Present → edit mode (productId read-only); absent/null → create mode. */
  product?: AdminProduct | null
  submitting?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submit', payload: ProductFormPayload): void
}>()

const formRef = ref<FormInstance>()
const form = reactive<ProductFormPayload>({
  productId: '',
  name: '',
  description: '',
  imageUrl: '',
  price: 0.01,
})

const isEdit = ref(false)
const imageError = ref(false)

const rules: FormRules = {
  productId: [
    { required: true, message: '请输入商品 ID', trigger: 'blur' },
    { max: 64, message: '商品 ID 长度不能超过 64', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { max: 128, message: '名称长度不能超过 128', trigger: 'blur' },
  ],
  description: [{ max: 1024, message: '描述长度不能超过 1024', trigger: 'blur' }],
  imageUrl: [{ max: 512, message: '图片地址长度不能超过 512', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
}

// Populate (edit) or reset (create) each time the dialog opens.
watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return
    }
    imageError.value = false
    if (props.product) {
      isEdit.value = true
      form.productId = props.product.productId
      form.name = props.product.name
      form.description = props.product.description ?? ''
      form.imageUrl = props.product.imageUrl ?? ''
      form.price = props.product.price
    } else {
      isEdit.value = false
      form.productId = ''
      form.name = ''
      form.description = ''
      form.imageUrl = ''
      form.price = 0.01
    }
    formRef.value?.clearValidate()
  },
)

function close(): void {
  emit('update:modelValue', false)
}

async function onConfirm(): Promise<void> {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) {
    return
  }
  emit('submit', { ...form })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑商品' : '新增商品'"
    width="520px"
    @update:model-value="close"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
      <el-form-item label="商品 ID" prop="productId">
        <el-input v-model="form.productId" :disabled="isEdit" placeholder="唯一商品编号" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="商品名称" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="商品描述（选填）" />
      </el-form-item>
      <el-form-item label="图片地址" prop="imageUrl">
        <el-input v-model="form.imageUrl" placeholder="https://… （选填）" @input="imageError = false" />
        <div v-if="form.imageUrl" class="image-preview">
          <img v-if="!imageError" :src="form.imageUrl" alt="预览" @error="imageError = true" />
          <span v-else class="image-fallback">图片无法加载</span>
        </div>
      </el-form-item>
      <el-form-item label="价格" prop="price">
        <el-input-number v-model="form.price" :min="0.01" :step="0.01" :precision="2" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onConfirm">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.image-preview {
  margin-top: var(--space-8);
}

.image-preview img {
  max-width: 120px;
  max-height: 120px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  object-fit: cover;
}

.image-fallback {
  font-size: var(--text-xs);
  color: var(--text-faint);
}
</style>
