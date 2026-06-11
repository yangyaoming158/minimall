import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import type { AiSuggestion } from '@/types/ai'
import { ApiError } from '@/types/api'

vi.mock('@/api/aiSuggestions', () => ({
  listAiSuggestions: vi.fn(),
  getAiSuggestion: vi.fn(),
  rejectAiSuggestion: vi.fn(),
  convertAiSuggestionToInboundDraft: vi.fn(),
}))

import {
  convertAiSuggestionToInboundDraft,
  getAiSuggestion,
  listAiSuggestions,
  rejectAiSuggestion,
} from '@/api/aiSuggestions'
import AiSuggestionsView from '@/views/AiSuggestionsView.vue'

const mockedList = vi.mocked(listAiSuggestions)
const mockedGet = vi.mocked(getAiSuggestion)
const mockedReject = vi.mocked(rejectAiSuggestion)
const mockedConvert = vi.mocked(convertAiSuggestionToInboundDraft)

function suggestion(over: Partial<AiSuggestion> = {}): AiSuggestion {
  return {
    suggestionNo: 'AIS-TEST-1',
    type: 'REPLENISHMENT',
    status: 'PENDING_REVIEW',
    source: 'AI_MODEL',
    reason: '低库存补货建议',
    inputSnapshotRef: 'snapshot-ref',
    inputSummary: 'lowStockLimit=20, salesDays=7',
    linkedInboundNo: null,
    rejectedReason: null,
    reviewedByAdminUserId: null,
    reviewedByAdminUsername: null,
    reviewedAt: null,
    itemCount: 1,
    totalSuggestedQuantity: 8,
    items: [
      {
        productId: 'SKU-LOW-1',
        productName: '低库存商品',
        availableStock: 2,
        lockedStock: 1,
        safetyStock: 5,
        soldQuantityLast7Days: 9,
        suggestedQuantity: 8,
        riskLevel: 'HIGH',
        reason: '可用库存低于安全库存',
      },
    ],
    createdAt: '2026-06-11T10:00:00',
    updatedAt: '2026-06-11T10:00:00',
    ...over,
  }
}

function pageOf(content: AiSuggestion[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

// Stub Teleport so drawer/dialog content renders in place, and stub ElSelect
// (status filter), which hits a render recursion under the Teleport stub.
function mountView() {
  return mount(AiSuggestionsView, {
    global: { plugins: [createPinia(), ElementPlus], stubs: { teleport: true, ElSelect: true } },
  })
}

async function clickButton(wrapper: VueWrapper, text: string): Promise<void> {
  const btn = wrapper.findAll('button').find((b) => b.text() === text)
  expect(btn, `button "${text}" not found`).toBeTruthy()
  await btn!.trigger('click')
  await flushPromises()
}

enableAutoUnmount(afterEach)

describe('AiSuggestionsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue(pageOf([suggestion()]))
  })

  it('loads the first page on mount and renders review fields', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(mockedList).toHaveBeenCalledWith({ page: 0, size: 10 })
    const text = wrapper.text()
    expect(text).toContain('AIS-TEST-1')
    expect(text).toContain('补货建议')
    expect(text).toContain('待审核')
    expect(text).toContain('AI 模型')
  })

  it('states that AI never executes stock changes', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('AI 不会直接变更库存')
    expect(wrapper.text()).toContain('入库单确认后')
  })

  it('opens the detail drawer with items, evidence summary and risk level', async () => {
    mockedGet.mockResolvedValue(suggestion())
    const wrapper = mountView()
    await flushPromises()

    await clickButton(wrapper, '详情')

    expect(mockedGet).toHaveBeenCalledWith('AIS-TEST-1')
    const text = wrapper.text()
    expect(text).toContain('SKU-LOW-1')
    expect(text).toContain('lowStockLimit=20, salesDays=7') // data range / input summary
    expect(text).toContain('低库存补货建议')
    expect(text).toContain('高') // riskLevel HIGH
    expect(text).toContain('可用库存低于安全库存')
  })

  it('rejects a pending suggestion with a required reason and refreshes', async () => {
    mockedReject.mockResolvedValue(suggestion({ status: 'REJECTED', rejectedReason: '近期已补货' }))
    const wrapper = mountView()
    await flushPromises()
    expect(mockedList).toHaveBeenCalledTimes(1)

    await clickButton(wrapper, '驳回')
    await wrapper.find('textarea').setValue('近期已补货')
    await clickButton(wrapper, '确认驳回')

    expect(mockedReject).toHaveBeenCalledWith('AIS-TEST-1', { reason: '近期已补货' })
    expect(mockedList).toHaveBeenCalledTimes(2)
  })

  it('blocks rejection without a reason', async () => {
    const warning = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({}) as never)
    const wrapper = mountView()
    await flushPromises()

    await clickButton(wrapper, '驳回')
    await clickButton(wrapper, '确认驳回')

    expect(mockedReject).not.toHaveBeenCalled()
    expect(warning).toHaveBeenCalledWith('请填写驳回原因')
  })

  it('converts a pending suggestion to an inbound draft after confirmation', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    mockedConvert.mockResolvedValue(
      suggestion({ status: 'CONVERTED_TO_DRAFT', linkedInboundNo: 'IB-20260611-1' }),
    )
    const wrapper = mountView()
    await flushPromises()

    await clickButton(wrapper, '转入库草稿')

    expect(mockedConvert).toHaveBeenCalledWith('AIS-TEST-1')
    expect(mockedList).toHaveBeenCalledTimes(2)
  })

  it('does not convert when the admin cancels the confirmation', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    const wrapper = mountView()
    await flushPromises()

    await clickButton(wrapper, '转入库草稿')

    expect(mockedConvert).not.toHaveBeenCalled()
  })

  it('surfaces a 40900 conflict when the suggestion was already reviewed', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as never)
    mockedConvert.mockRejectedValue(new ApiError('40900', '建议已被审核', 409))
    const wrapper = mountView()
    await flushPromises()

    await clickButton(wrapper, '转入库草稿')

    expect(error).toHaveBeenCalledWith('建议已被审核')
  })

  it('hides review actions for non-pending suggestions', async () => {
    mockedList.mockResolvedValue(
      pageOf([suggestion({ status: 'APPLIED', linkedInboundNo: 'IB-20260611-1' })]),
    )
    const wrapper = mountView()
    await flushPromises()

    const labels = wrapper.findAll('button').map((b) => b.text())
    expect(labels).not.toContain('转入库草稿')
    expect(labels).not.toContain('驳回')
    expect(wrapper.text()).toContain('IB-20260611-1')
  })

  it('filters by status', async () => {
    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as unknown as { statusFilter: string; onSearch: () => void }
    vm.statusFilter = 'PENDING_REVIEW'
    vm.onSearch()
    await flushPromises()

    expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 10, status: 'PENDING_REVIEW' })
  })

  it('shows an error hint when the list fails to load', async () => {
    mockedList.mockRejectedValue(new ApiError('50000', '服务器开小差了', 500))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('加载失败，请稍后重试。')
  })
})
