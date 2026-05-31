import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, enableAutoUnmount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import type { AdminAuditLog } from '@/types/audit'

vi.mock('@/api/auditLogs', () => ({
  listAuditLogs: vi.fn(),
}))

import { listAuditLogs } from '@/api/auditLogs'
import AuditLogsView from '@/views/AuditLogsView.vue'

const mockedList = vi.mocked(listAuditLogs)

function auditLog(over: Partial<AdminAuditLog> = {}): AdminAuditLog {
  return {
    id: 9,
    adminUserId: 1,
    adminUsername: 'admin',
    action: 'INVENTORY_ADJUST',
    resourceType: 'INVENTORY',
    resourceId: 'SKU-1',
    requestId: 'req-x',
    sourceType: 'INVENTORY_ADJUSTMENT',
    referenceNo: 'REF-1',
    beforeSnapshot: { availableStock: 10 },
    afterSnapshot: { availableStock: 18 },
    ip: '127.0.0.1',
    userAgent: 'vitest',
    summary: '调整库存 SKU-1',
    createdAt: '2026-05-31T08:00:00Z',
    ...over,
  }
}

function pageOf(content: AdminAuditLog[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function mountView() {
  return mount(AuditLogsView, { global: { plugins: [createPinia(), ElementPlus] } })
}

// For asserting the teleported detail drawer inline: stub Teleport and the
// page's ElSelect/ElDatePicker (teleported popovers recurse under the stub).
function mountViewInline() {
  return mount(AuditLogsView, {
    global: {
      plugins: [createPinia(), ElementPlus],
      stubs: { teleport: true, ElSelect: true, ElDatePicker: true },
    },
  })
}

async function openDetail(wrapper: VueWrapper): Promise<void> {
  const btn = wrapper.findAll('button').find((b) => b.text() === '详情')
  await btn!.trigger('click')
  await flushPromises()
}

enableAutoUnmount(afterEach)

describe('AuditLogsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue(pageOf([auditLog()]))
  })

  it('loads the first page and renders traceability fields', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(mockedList).toHaveBeenCalledWith({ page: 0, size: 10 })
    const text = wrapper.text()
    expect(text).toContain('admin') // operator
    expect(text).toContain('调整库存') // action label
    expect(text).toContain('SKU-1') // resourceId
    expect(text).toContain('INVENTORY_ADJUSTMENT') // sourceType (traceability)
    expect(text).toContain('REF-1') // referenceNo (traceability)
    expect(text).toContain('req-x') // requestId (traceability)
  })

  it('searches with the resourceId filter', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[placeholder="资源 ID"]').setValue('SKU-9')
    const searchBtn = wrapper.findAll('button').find((b) => b.text() === '搜索')
    await searchBtn!.trigger('click')
    await flushPromises()

    expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 10, resourceId: 'SKU-9' })
  })

  it('opens the detail drawer with summary and before/after snapshots', async () => {
    const wrapper = mountViewInline()
    await flushPromises()
    await openDetail(wrapper)

    const text = wrapper.text()
    expect(text).toContain('调整库存 SKU-1') // summary
    expect(text).toContain('availableStock') // snapshot key
    expect(text).toContain('18') // afterSnapshot value
  })
})
