import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  mount,
  flushPromises,
  enableAutoUnmount,
  RouterLinkStub,
  type VueWrapper,
} from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import type {
  AiAnalysisResponse,
  AiInventoryAskResponse,
  AiSuggestion,
} from '@/types/ai'
import { ApiError } from '@/types/api'

vi.mock('@/api/ai', () => ({
  askInventoryQuestion: vi.fn(),
  runLowStockAnalysis: vi.fn(),
  runHotProductsAnalysis: vi.fn(),
  generateReplenishmentSuggestion: vi.fn(),
}))

import {
  askInventoryQuestion,
  generateReplenishmentSuggestion,
  runHotProductsAnalysis,
  runLowStockAnalysis,
} from '@/api/ai'
import AiInventoryView from '@/views/AiInventoryView.vue'

const mockedAsk = vi.mocked(askInventoryQuestion)
const mockedLowStock = vi.mocked(runLowStockAnalysis)
const mockedHot = vi.mocked(runHotProductsAnalysis)
const mockedGenerate = vi.mocked(generateReplenishmentSuggestion)

function askResponse(over: Partial<AiInventoryAskResponse> = {}): AiInventoryAskResponse {
  return {
    intent: 'CURRENT_STOCK',
    supported: true,
    answer: 'SKU-1 当前可用库存 12 件，锁定 1 件。',
    queryTime: '2026-06-11T12:00:00',
    evidence: {
      evidenceType: 'CURRENT_INVENTORY',
      generatedAt: '2026-06-11T12:00:00',
      dataFrom: '2026-06-04T12:00:00',
      dataTo: '2026-06-11T12:00:00',
      inventories: [
        {
          productId: 'SKU-1',
          availableStock: 12,
          lockedStock: 1,
          safetyStock: 5,
          status: 'ACTIVE',
          stockState: 'IN_STOCK',
          lowStock: false,
          createdAt: '2026-05-01T00:00:00',
          updatedAt: '2026-06-11T00:00:00',
        },
      ],
      records: [],
    },
    limitations: ['仅基于近 7 天数据'],
    ...over,
  }
}

function analysisResponse(over: Partial<AiAnalysisResponse> = {}): AiAnalysisResponse {
  return {
    analysisType: 'LOW_STOCK',
    summary: '2 个商品可用库存低于安全库存，建议优先补货。',
    queryTime: '2026-06-11T12:00:00',
    evidence: {
      evidenceType: 'LOW_STOCK_ANALYSIS',
      days: 7,
      generatedAt: '2026-06-11T12:00:00',
      dataFrom: '2026-06-04T12:00:00',
      dataTo: '2026-06-11T12:00:00',
      limitations: [],
      products: [
        {
          productId: 'SKU-LOW-1',
          rank: 1,
          inventory: {
            productId: 'SKU-LOW-1',
            availableStock: 2,
            lockedStock: 0,
            safetyStock: 5,
            status: 'ACTIVE',
            stockState: 'IN_STOCK',
            lowStock: true,
            createdAt: '2026-05-01T00:00:00',
            updatedAt: '2026-06-11T00:00:00',
          },
          sales: { productId: 'SKU-LOW-1', soldQuantity: 9, orderCount: 4, totalAmount: '199.00' },
          records: [],
          limitations: [],
        },
      ],
    },
    items: [
      {
        productId: 'SKU-LOW-1',
        productName: '低库存商品',
        availableStock: 2,
        lockedStock: 0,
        safetyStock: 5,
        soldQuantityLast7Days: 9,
        riskLevel: 'HIGH',
        reason: '可用库存低于安全库存',
      },
    ],
    limitations: ['未包含在途库存'],
    ...over,
  }
}

function generatedSuggestion(over: Partial<AiSuggestion> = {}): AiSuggestion {
  return {
    suggestionNo: 'AIS-GEN-1',
    type: 'REPLENISHMENT',
    status: 'PENDING_REVIEW',
    source: 'AI_MODEL',
    reason: '低库存补货建议',
    inputSnapshotRef: 'ref',
    inputSummary: 'lowStockLimit=20, salesDays=7',
    linkedInboundNo: null,
    rejectedReason: null,
    reviewedByAdminUserId: null,
    reviewedByAdminUsername: null,
    reviewedAt: null,
    itemCount: 1,
    totalSuggestedQuantity: 8,
    items: [],
    createdAt: '2026-06-11T12:00:00',
    updatedAt: '2026-06-11T12:00:00',
    ...over,
  }
}

function mountView() {
  return mount(AiInventoryView, {
    global: {
      plugins: [createPinia(), ElementPlus],
      stubs: { teleport: true, RouterLink: RouterLinkStub },
    },
  })
}

async function clickButton(wrapper: VueWrapper, text: string): Promise<void> {
  const btn = wrapper.findAll('button').find((b) => b.text() === text)
  expect(btn, `button "${text}" not found`).toBeTruthy()
  await btn!.trigger('click')
  await flushPromises()
}

enableAutoUnmount(afterEach)

describe('AiInventoryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('states that AI never executes stock changes', () => {
    const wrapper = mountView()

    expect(wrapper.text()).toContain('不会执行任何库存变更')
    expect(wrapper.text()).toContain('库存仅在入库单确认后变化')
  })

  it('asks a question and renders answer, intent, time range and evidence', async () => {
    mockedAsk.mockResolvedValue(askResponse())
    const wrapper = mountView()

    await wrapper.find('input[placeholder="例如：SKU-1 当前库存多少？哪些商品低库存？"]').setValue('SKU-1 库存多少')
    await clickButton(wrapper, '提问')

    expect(mockedAsk).toHaveBeenCalledWith({ question: 'SKU-1 库存多少' })
    const text = wrapper.text()
    expect(text).toContain('SKU-1 当前可用库存 12 件')
    expect(text).toContain('当前库存') // intent label
    expect(text).toContain('数据时间范围')
    expect(text).toContain('2026-06-04T12:00:00 ~ 2026-06-11T12:00:00')
    expect(text).toContain('库存证据')
    expect(text).toContain('仅基于近 7 天数据') // limitations
  })

  it('passes the optional productId filter when asking', async () => {
    mockedAsk.mockResolvedValue(askResponse())
    const wrapper = mountView()

    await wrapper.find('input[placeholder="例如：SKU-1 当前库存多少？哪些商品低库存？"]').setValue('库存多少')
    await wrapper.find('input[placeholder="商品 ID（可选）"]').setValue('SKU-1')
    await clickButton(wrapper, '提问')

    expect(mockedAsk).toHaveBeenCalledWith({ question: '库存多少', productId: 'SKU-1' })
  })

  it('renders a distinct state for unsupported questions', async () => {
    mockedAsk.mockResolvedValue(
      askResponse({ intent: 'UNSUPPORTED', supported: false, answer: '该问题超出库存问答范围。', evidence: null }),
    )
    const wrapper = mountView()

    await wrapper.find('input[placeholder="例如：SKU-1 当前库存多少？哪些商品低库存？"]').setValue('明天会下雨吗')
    await clickButton(wrapper, '提问')

    expect(wrapper.text()).toContain('暂不支持的问题')
    expect(wrapper.text()).toContain('该问题超出库存问答范围。')
  })

  it('blocks asking with an empty question', async () => {
    const wrapper = mountView()

    await clickButton(wrapper, '提问')

    expect(mockedAsk).not.toHaveBeenCalled()
  })

  it('runs low-stock analysis and renders summary, risk and structured evidence', async () => {
    mockedLowStock.mockResolvedValue(analysisResponse())
    const wrapper = mountView()

    await clickButton(wrapper, '开始低库存分析')

    expect(mockedLowStock).toHaveBeenCalledWith({})
    const text = wrapper.text()
    expect(text).toContain('2 个商品可用库存低于安全库存')
    expect(text).toContain('SKU-LOW-1')
    expect(text).toContain('高') // riskLevel HIGH
    expect(text).toContain('结构化证据')
    expect(text).toContain('数据时间范围')
    expect(text).toContain('未包含在途库存') // limitations
  })

  it('runs hot-products analysis with the selected day window', async () => {
    mockedHot.mockResolvedValue(analysisResponse({ analysisType: 'HOT_PRODUCTS' }))
    const wrapper = mountView()

    const thirtyDays = wrapper.findAll('input[type="radio"]')[1]
    await thirtyDays.setValue()
    await clickButton(wrapper, '开始热销分析')

    expect(mockedHot).toHaveBeenCalledWith({ days: 30 })
  })

  it('shows the missing-data state on a 40900 conflict', async () => {
    mockedLowStock.mockRejectedValue(new ApiError('40900', '没有可分析的库存数据', 409))
    const wrapper = mountView()

    await clickButton(wrapper, '开始低库存分析')

    expect(wrapper.text()).toContain('数据不足')
    expect(wrapper.text()).toContain('没有可分析的库存数据')
  })

  it('shows the validation-failure state on a 40001 error', async () => {
    mockedLowStock.mockRejectedValue(new ApiError('40001', 'AI output validation failed: unknown productId', 400))
    const wrapper = mountView()

    await clickButton(wrapper, '开始低库存分析')

    expect(wrapper.text()).toContain('校验失败')
    expect(wrapper.text()).toContain('模型输出未通过后端校验')
  })

  it('shows the model-failure state on a 50000 error', async () => {
    mockedHot.mockRejectedValue(new ApiError('50000', 'AI provider request failed', 500))
    const wrapper = mountView()

    await clickButton(wrapper, '开始热销分析')

    expect(wrapper.text()).toContain('模型服务失败')
  })

  it('generates a suggestion and links to the review page without claiming stock changes', async () => {
    mockedGenerate.mockResolvedValue(generatedSuggestion())
    const wrapper = mountView()

    await clickButton(wrapper, '生成补货建议')

    expect(mockedGenerate).toHaveBeenCalledWith({})
    const text = wrapper.text()
    expect(text).toContain('AIS-GEN-1')
    expect(text).toContain('待审核')
    expect(text).toContain('lowStockLimit=20, salesDays=7') // data range
    expect(text).toContain('前往 AI 建议审批')
    expect(text).not.toContain('已入库')
  })

  it('shows the missing-data state when generation has no evidence', async () => {
    mockedGenerate.mockRejectedValue(new ApiError('40900', '缺少可用于生成建议的库存数据', 409))
    const wrapper = mountView()

    await clickButton(wrapper, '生成补货建议')

    expect(wrapper.text()).toContain('数据不足')
    expect(wrapper.text()).toContain('缺少可用于生成建议的库存数据')
  })
})
