<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  convertAiSuggestionToInboundDraft,
  getAiSuggestion,
  listAiSuggestions,
  rejectAiSuggestion,
} from '@/api/aiSuggestions'
import { ApiError } from '@/types/api'
import type {
  AiSuggestion,
  AiSuggestionRiskLevel,
  AiSuggestionSource,
  AiSuggestionStatus,
  AiSuggestionType,
} from '@/types/ai'

const PAGE_SIZE = 10

const suggestions = ref<AiSuggestion[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errored = ref(false)

const statusFilter = ref<'' | AiSuggestionStatus>('')

const STATUS_OPTIONS: { value: '' | AiSuggestionStatus; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'CONVERTED_TO_DRAFT', label: '已转入库草稿' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'APPLIED', label: '已生效' },
]

const STATUS_LABEL: Record<AiSuggestionStatus, string> = {
  PENDING_REVIEW: '待审核',
  CONVERTED_TO_DRAFT: '已转入库草稿',
  REJECTED: '已驳回',
  APPLIED: '已生效',
}

const STATUS_TONE: Record<AiSuggestionStatus, 'success' | 'warning' | 'danger' | 'info'> = {
  PENDING_REVIEW: 'warning',
  CONVERTED_TO_DRAFT: 'info',
  REJECTED: 'danger',
  APPLIED: 'success',
}

const TYPE_LABEL: Record<AiSuggestionType, string> = {
  REPLENISHMENT: '补货建议',
  DAILY_REPORT: '日报',
}

const SOURCE_LABEL: Record<AiSuggestionSource, string> = {
  AI_MODEL: 'AI 模型',
  SYSTEM_GENERATED: '系统生成',
  ADMIN_MANUAL: '管理员手动',
}

const RISK_LABEL: Record<AiSuggestionRiskLevel, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

const RISK_TONE: Record<AiSuggestionRiskLevel, 'success' | 'warning' | 'danger'> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'danger',
}

// Detail drawer state.
const detailVisible = ref(false)
const detail = ref<AiSuggestion | null>(null)
const detailLoading = ref(false)

// Reject dialog state.
const rejectVisible = ref(false)
const rejectTarget = ref<string | null>(null)
const rejectReason = ref('')
const rejectSubmitting = ref(false)

const convertSubmitting = ref(false)

// The http interceptor only toasts 401/429/500. Surface other business errors
// (e.g. 40900 status conflict) explicitly; transport-less errors were toasted.
function surfaceBusinessError(err: unknown): void {
  if (err instanceof ApiError && err.httpStatus != null && ![401, 429, 500].includes(err.httpStatus)) {
    ElMessage.error(err.message)
  }
}

async function fetchSuggestions(): Promise<void> {
  loading.value = true
  errored.value = false
  try {
    const res = await listAiSuggestions({
      page: page.value - 1,
      size: PAGE_SIZE,
      ...(statusFilter.value ? { status: statusFilter.value } : {}),
    })
    suggestions.value = res.content
    total.value = res.totalElements
  } catch {
    errored.value = true
    suggestions.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  fetchSuggestions()
}

function onPageChange(next: number): void {
  page.value = next
  fetchSuggestions()
}

// Re-fetch the suggestion so the drawer reflects the latest review state even
// if the list page is stale; fall back to row data if the detail call fails.
async function openDetail(row: AiSuggestion): Promise<void> {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getAiSuggestion(row.suggestionNo)
  } catch {
    /* surfaced by the http interceptor; keep the row snapshot */
  } finally {
    detailLoading.value = false
  }
}

function openReject(row: AiSuggestion): void {
  rejectTarget.value = row.suggestionNo
  rejectReason.value = ''
  rejectVisible.value = true
}

async function onRejectSubmit(): Promise<void> {
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  if (rejectSubmitting.value || !rejectTarget.value) {
    return // in-flight guard: a re-fired submit must not double-apply
  }
  rejectSubmitting.value = true
  try {
    await rejectAiSuggestion(rejectTarget.value, { reason })
    ElMessage.success('建议已驳回')
    rejectVisible.value = false
    detailVisible.value = false
    await fetchSuggestions()
  } catch (err) {
    surfaceBusinessError(err) // e.g. 40900 already reviewed — keep dialog open
  } finally {
    rejectSubmitting.value = false
  }
}

async function onConvert(row: AiSuggestion): Promise<void> {
  if (convertSubmitting.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      '将建议转为入库草稿。此操作不会变更库存：库存仅在入库单确认后才会变化。',
      '转入库草稿',
      { confirmButtonText: '生成草稿', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return // admin cancelled
  }
  convertSubmitting.value = true
  try {
    const converted = await convertAiSuggestionToInboundDraft(row.suggestionNo)
    ElMessage.success(`已生成入库草稿 ${converted.linkedInboundNo ?? ''}，请到入库单页面确认后才会变更库存`)
    detailVisible.value = false
    await fetchSuggestions()
  } catch (err) {
    surfaceBusinessError(err)
  } finally {
    convertSubmitting.value = false
  }
}

onMounted(fetchSuggestions)
</script>

<template>
  <div>
    <PageHeader
      title="AI 建议审批"
      description="AI 生成的补货建议在此审批：驳回，或转为入库草稿。AI 不会直接变更库存，库存仅在入库单确认后变化。"
    />

    <div class="filter-bar">
      <el-select v-model="statusFilter" placeholder="建议状态" style="width: 160px" @change="onSearch">
        <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button type="primary" plain @click="onSearch">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="suggestions" class="table" empty-text="暂无 AI 建议">
      <el-table-column prop="suggestionNo" label="建议单号" min-width="200" show-overflow-tooltip />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ TYPE_LABEL[row.type as AiSuggestionType] }}</template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <StatusTag
            :value="row.status"
            :label="STATUS_LABEL[row.status as AiSuggestionStatus]"
            :tone="STATUS_TONE[row.status as AiSuggestionStatus]"
          />
        </template>
      </el-table-column>
      <el-table-column label="来源" width="110">
        <template #default="{ row }">{{ SOURCE_LABEL[row.source as AiSuggestionSource] }}</template>
      </el-table-column>
      <el-table-column label="商品数" width="90" align="right">
        <template #default="{ row }">{{ row.itemCount }}</template>
      </el-table-column>
      <el-table-column label="建议总量" width="100" align="right">
        <template #default="{ row }">{{ row.totalSuggestedQuantity }}</template>
      </el-table-column>
      <el-table-column label="关联入库单" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <router-link
            v-if="row.linkedInboundNo"
            class="mono inbound-link"
            :to="{ path: '/inbound-orders', query: { inboundNo: row.linkedInboundNo } }"
          >
            {{ row.linkedInboundNo }}
          </router-link>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <template v-if="row.status === 'PENDING_REVIEW'">
            <el-button link type="primary" @click="onConvert(row)">转入库草稿</el-button>
            <el-button link type="danger" @click="openReject(row)">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="errored" class="error-hint">加载失败，请稍后重试。</div>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next, total"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <el-drawer v-model="detailVisible" title="建议详情" size="640px">
      <div v-if="detail" v-loading="detailLoading" class="detail">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="AI 仅产生建议，不执行库存变更；库存仅在关联入库单确认后变化。"
        />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="建议单号" :span="2">
            <span class="mono">{{ detail.suggestionNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ TYPE_LABEL[detail.type] }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detail.status" :label="STATUS_LABEL[detail.status]" :tone="STATUS_TONE[detail.status]" />
          </el-descriptions-item>
          <el-descriptions-item label="来源">{{ SOURCE_LABEL[detail.source] }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.inputSummary" label="数据范围" :span="2">
            {{ detail.inputSummary }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.reason" label="建议说明" :span="2">
            {{ detail.reason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.linkedInboundNo" label="关联入库单" :span="2">
            <router-link
              class="mono inbound-link"
              :to="{ path: '/inbound-orders', query: { inboundNo: detail.linkedInboundNo } }"
            >
              {{ detail.linkedInboundNo }}
            </router-link>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectedReason" label="驳回原因" :span="2">
            {{ detail.rejectedReason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.reviewedByAdminUsername" label="审核人">
            {{ detail.reviewedByAdminUsername }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.reviewedAt" label="审核时间">
            {{ detail.reviewedAt }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.modelProvider" label="模型提供方">
            {{ detail.modelProvider }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.modelName" label="模型">
            <span class="mono">{{ detail.modelName }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.promptVersion" label="Prompt 版本">
            <span class="mono">{{ detail.promptVersion }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.outputSchemaVersion" label="输出 Schema 版本">
            <span class="mono">{{ detail.outputSchemaVersion }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.validationStatus" label="后端校验">
            <StatusTag
              :value="detail.validationStatus"
              :label="detail.validationStatus === 'VALID' ? '已通过' : '未通过'"
              :tone="detail.validationStatus === 'VALID' ? 'success' : 'danger'"
            />
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.validationError" label="校验错误" :span="2">
            {{ detail.validationError }}
          </el-descriptions-item>
        </el-descriptions>

        <h3 class="items-title">建议明细（{{ detail.itemCount }} 项，合计 {{ detail.totalSuggestedQuantity }}）</h3>
        <el-table :data="detail.items" size="small" empty-text="无明细">
          <el-table-column prop="productId" label="商品 ID" min-width="140" show-overflow-tooltip />
          <el-table-column label="可用" width="70" align="right">
            <template #default="{ row }">{{ row.availableStock ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="安全" width="70" align="right">
            <template #default="{ row }">{{ row.safetyStock ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="近 7 天销量" width="100" align="right">
            <template #default="{ row }">{{ row.soldQuantityLast7Days ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="建议补货" width="90" align="right">
            <template #default="{ row }">{{ row.suggestedQuantity }}</template>
          </el-table-column>
          <el-table-column label="风险" width="80">
            <template #default="{ row }">
              <StatusTag
                v-if="row.riskLevel"
                :value="row.riskLevel"
                :label="RISK_LABEL[row.riskLevel as AiSuggestionRiskLevel]"
                :tone="RISK_TONE[row.riskLevel as AiSuggestionRiskLevel]"
              />
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="理由" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.reason ?? '—' }}</template>
          </el-table-column>
        </el-table>

        <div v-if="detail.status === 'PENDING_REVIEW'" class="detail-actions">
          <el-button type="primary" :loading="convertSubmitting" @click="onConvert(detail)">
            转入库草稿
          </el-button>
          <el-button type="danger" plain @click="openReject(detail)">驳回</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="rejectVisible" title="驳回建议" width="460px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        maxlength="512"
        show-word-limit
        placeholder="请填写驳回原因（必填）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSubmitting" @click="onRejectSubmit">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--space-12);
  margin-bottom: var(--space-16);
}

.table {
  width: 100%;
}

.muted {
  color: var(--text-faint);
}

.error-hint {
  margin-top: var(--space-12);
  color: var(--danger);
  font-size: var(--text-sm);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-16);
}

.detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-16);
}

.items-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-strong);
}

.detail-actions {
  display: flex;
  gap: var(--space-8);
  justify-content: flex-end;
}

.mono {
  font-family: var(--font-mono, monospace);
}

.inbound-link {
  color: var(--accent, #409eff);
  text-decoration: none;
}

.inbound-link:hover {
  text-decoration: underline;
}
</style>
