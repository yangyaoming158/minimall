<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import AiAnalysisResult from '@/components/AiAnalysisResult.vue'
import {
  askInventoryQuestion,
  generateReplenishmentSuggestion,
  runHotProductsAnalysis,
  runLowStockAnalysis,
} from '@/api/ai'
import { ApiError } from '@/types/api'
import type {
  AiAnalysisResponse,
  AiInventoryAskResponse,
  AiInventoryQuestionIntent,
  AiSuggestion,
} from '@/types/ai'

const INTENT_LABEL: Record<AiInventoryQuestionIntent, string> = {
  CURRENT_STOCK: '当前库存',
  LOW_STOCK_LIST: '低库存清单',
  PRODUCT_STATUS: '商品状态',
  RECENT_RECORDS: '近期流水',
  UNSUPPORTED: '暂不支持',
}

// Distinct failure surfaces required by the contract: backend validation
// failure (40001), missing data (40900), model/provider failure (50000),
// anything else generic. Unsupported questions are a successful response with
// supported=false, not an error.
interface AiPanelError {
  kind: '校验失败' | '数据不足' | '模型服务失败' | '请求失败'
  message: string
}

function toPanelError(err: unknown): AiPanelError {
  if (err instanceof ApiError) {
    if (err.code === '40001') {
      return { kind: '校验失败', message: `模型输出未通过后端校验，结果已被拒绝：${err.message}` }
    }
    if (err.code === '40900') {
      return { kind: '数据不足', message: err.message }
    }
    if (err.code === '50000') {
      return { kind: '模型服务失败', message: 'AI 模型服务调用失败，请稍后重试或检查模型配置。' }
    }
    return { kind: '请求失败', message: err.message }
  }
  return { kind: '请求失败', message: '请求失败，请稍后重试' }
}

// ---- Q&A panel ----
const question = ref('')
const askProductId = ref('')
const askLoading = ref(false)
const askError = ref<AiPanelError | null>(null)
const askResult = ref<AiInventoryAskResponse | null>(null)

async function onAsk(): Promise<void> {
  const trimmed = question.value.trim()
  if (!trimmed) {
    ElMessage.warning('请输入问题')
    return
  }
  if (askLoading.value) {
    return
  }
  askLoading.value = true
  askError.value = null
  askResult.value = null
  try {
    askResult.value = await askInventoryQuestion({
      question: trimmed,
      ...(askProductId.value.trim() ? { productId: askProductId.value.trim() } : {}),
    })
  } catch (err) {
    askError.value = toPanelError(err)
  } finally {
    askLoading.value = false
  }
}

function formatRange(from: string | null, to: string | null): string {
  if (!from && !to) {
    return '未提供'
  }
  return `${from ?? '—'} ~ ${to ?? '—'}`
}

// ---- Low-stock analysis panel ----
const lowStockLoading = ref(false)
const lowStockError = ref<AiPanelError | null>(null)
const lowStockResult = ref<AiAnalysisResponse | null>(null)

async function onLowStockAnalysis(): Promise<void> {
  if (lowStockLoading.value) {
    return
  }
  lowStockLoading.value = true
  lowStockError.value = null
  lowStockResult.value = null
  try {
    lowStockResult.value = await runLowStockAnalysis({})
  } catch (err) {
    lowStockError.value = toPanelError(err)
  } finally {
    lowStockLoading.value = false
  }
}

// ---- Hot-products analysis panel ----
const hotDays = ref<7 | 30>(7)
const hotLoading = ref(false)
const hotError = ref<AiPanelError | null>(null)
const hotResult = ref<AiAnalysisResponse | null>(null)

async function onHotProductsAnalysis(): Promise<void> {
  if (hotLoading.value) {
    return
  }
  hotLoading.value = true
  hotError.value = null
  hotResult.value = null
  try {
    hotResult.value = await runHotProductsAnalysis({ days: hotDays.value })
  } catch (err) {
    hotError.value = toPanelError(err)
  } finally {
    hotLoading.value = false
  }
}

// ---- Suggestion generation panel ----
const generateLoading = ref(false)
const generateError = ref<AiPanelError | null>(null)
const generated = ref<AiSuggestion | null>(null)

async function onGenerate(): Promise<void> {
  if (generateLoading.value) {
    return
  }
  generateLoading.value = true
  generateError.value = null
  generated.value = null
  try {
    generated.value = await generateReplenishmentSuggestion({})
    ElMessage.success('补货建议已生成，等待审核；审核与入库确认前库存不会变化')
  } catch (err) {
    generateError.value = toPanelError(err)
  } finally {
    generateLoading.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader
      title="AI 库存助手"
      description="库存问答、低库存分析、热销分析与补货建议生成。AI 仅基于后端只读数据产生分析和建议，不会执行任何库存变更；库存仅在入库单确认后变化。"
    />

    <el-tabs class="panels">
      <el-tab-pane label="库存问答">
        <div class="panel">
          <div class="form-row">
            <el-input
              v-model="question"
              placeholder="例如：SKU-1 当前库存多少？哪些商品低库存？"
              maxlength="1000"
              style="max-width: 480px"
              @keyup.enter="onAsk"
            />
            <el-input
              v-model="askProductId"
              placeholder="商品 ID（可选）"
              maxlength="64"
              style="width: 200px"
            />
            <el-button type="primary" :loading="askLoading" @click="onAsk">提问</el-button>
          </div>

          <el-alert
            v-if="askError"
            type="error"
            :closable="false"
            show-icon
            :title="askError.kind"
            :description="askError.message"
          />

          <template v-if="askResult">
            <el-alert
              v-if="!askResult.supported"
              type="warning"
              :closable="false"
              show-icon
              title="暂不支持的问题"
              :description="askResult.answer"
            />
            <div v-else class="ask-result">
              <div class="meta">
                <span class="meta-item">意图：{{ INTENT_LABEL[askResult.intent] }}</span>
                <span class="meta-item">回答时间：{{ askResult.queryTime }}</span>
                <span v-if="askResult.evidence" class="meta-item">
                  数据时间范围：{{ formatRange(askResult.evidence.dataFrom, askResult.evidence.dataTo) }}
                </span>
              </div>
              <p class="answer">{{ askResult.answer }}</p>

              <template v-if="askResult.evidence && askResult.evidence.inventories.length > 0">
                <h4 class="section-title">库存证据（后端只读数据）</h4>
                <el-table :data="askResult.evidence.inventories" size="small" empty-text="无数据">
                  <el-table-column prop="productId" label="商品 ID" min-width="140" show-overflow-tooltip />
                  <el-table-column label="可用" width="80" align="right">
                    <template #default="{ row }">{{ row.availableStock }}</template>
                  </el-table-column>
                  <el-table-column label="锁定" width="80" align="right">
                    <template #default="{ row }">{{ row.lockedStock }}</template>
                  </el-table-column>
                  <el-table-column label="安全" width="80" align="right">
                    <template #default="{ row }">{{ row.safetyStock }}</template>
                  </el-table-column>
                  <el-table-column label="低库存" width="80">
                    <template #default="{ row }">
                      <StatusTag v-if="row.lowStock" value="LOW_STOCK" label="低库存" tone="warning" />
                      <span v-else class="muted">—</span>
                    </template>
                  </el-table-column>
                </el-table>
              </template>

              <template v-if="askResult.evidence && askResult.evidence.records.length > 0">
                <h4 class="section-title">流水证据</h4>
                <el-table :data="askResult.evidence.records" size="small" empty-text="无数据">
                  <el-table-column prop="productId" label="商品 ID" min-width="130" show-overflow-tooltip />
                  <el-table-column prop="changeType" label="变更类型" width="140" />
                  <el-table-column label="数量" width="80" align="right">
                    <template #default="{ row }">{{ row.quantity }}</template>
                  </el-table-column>
                  <el-table-column prop="createdAt" label="时间" width="170" />
                </el-table>
              </template>

              <div v-if="askResult.limitations.length > 0" class="limitations">
                <h4 class="section-title">数据局限</h4>
                <ul>
                  <li v-for="(item, index) in askResult.limitations" :key="index">{{ item }}</li>
                </ul>
              </div>
            </div>
          </template>
        </div>
      </el-tab-pane>

      <el-tab-pane label="低库存分析">
        <div class="panel">
          <div class="form-row">
            <el-button type="primary" :loading="lowStockLoading" @click="onLowStockAnalysis">
              开始低库存分析
            </el-button>
            <span class="hint">基于安全库存与近 7 天销量，由 AI 模型生成分析；结果经后端校验。</span>
          </div>
          <el-alert
            v-if="lowStockError"
            type="error"
            :closable="false"
            show-icon
            :title="lowStockError.kind"
            :description="lowStockError.message"
          />
          <AiAnalysisResult v-if="lowStockResult" :result="lowStockResult" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="热销分析">
        <div class="panel">
          <div class="form-row">
            <el-radio-group v-model="hotDays">
              <el-radio-button :value="7">近 7 天</el-radio-button>
              <el-radio-button :value="30">近 30 天</el-radio-button>
            </el-radio-group>
            <el-button type="primary" :loading="hotLoading" @click="onHotProductsAnalysis">
              开始热销分析
            </el-button>
          </div>
          <el-alert
            v-if="hotError"
            type="error"
            :closable="false"
            show-icon
            :title="hotError.kind"
            :description="hotError.message"
          />
          <AiAnalysisResult v-if="hotResult" :result="hotResult" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="生成补货建议">
        <div class="panel">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="生成的建议仅进入待审核状态，不会创建入库单，也不会变更库存；需在 AI 建议审批页驳回或转入库草稿，并在入库单确认后库存才会变化。"
          />
          <div class="form-row">
            <el-button type="primary" :loading="generateLoading" @click="onGenerate">
              生成补货建议
            </el-button>
          </div>
          <el-alert
            v-if="generateError"
            type="error"
            :closable="false"
            show-icon
            :title="generateError.kind"
            :description="generateError.message"
          />
          <div v-if="generated" class="generated">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="建议单号" :span="2">
                <span class="mono">{{ generated.suggestionNo }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <StatusTag :value="generated.status" label="待审核" tone="warning" />
              </el-descriptions-item>
              <el-descriptions-item label="商品数 / 建议总量">
                {{ generated.itemCount }} 项 / {{ generated.totalSuggestedQuantity }} 件
              </el-descriptions-item>
              <el-descriptions-item v-if="generated.inputSummary" label="数据范围" :span="2">
                {{ generated.inputSummary }}
              </el-descriptions-item>
              <el-descriptions-item v-if="generated.reason" label="建议说明" :span="2">
                {{ generated.reason }}
              </el-descriptions-item>
            </el-descriptions>
            <router-link class="review-link" to="/ai-suggestions">前往 AI 建议审批 →</router-link>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.panels :deep(.el-tabs__content) {
  padding-top: var(--space-8);
}

.panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-16);
}

.form-row {
  display: flex;
  align-items: center;
  gap: var(--space-12);
  flex-wrap: wrap;
}

.hint {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.ask-result {
  display: flex;
  flex-direction: column;
  gap: var(--space-12);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-16);
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.answer {
  margin: 0;
  padding: var(--space-12) var(--space-16);
  background: var(--surface-muted, #f5f7fa);
  border-radius: var(--radius);
  color: var(--text);
  line-height: 1.7;
  white-space: pre-wrap;
}

.section-title {
  margin: var(--space-4) 0 0;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-strong);
}

.muted {
  color: var(--text-faint);
}

.limitations ul {
  margin: var(--space-4) 0 0;
  padding-left: var(--space-20);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.generated {
  display: flex;
  flex-direction: column;
  gap: var(--space-12);
  max-width: 720px;
}

.review-link {
  align-self: flex-start;
  color: var(--accent, #409eff);
  text-decoration: none;
  font-size: var(--text-sm);
}

.review-link:hover {
  text-decoration: underline;
}

.mono {
  font-family: var(--font-mono, monospace);
}
</style>
