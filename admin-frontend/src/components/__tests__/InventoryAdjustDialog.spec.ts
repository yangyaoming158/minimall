import { afterEach, describe, expect, it } from 'vitest'
import { mount, flushPromises, enableAutoUnmount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElInputNumber } from 'element-plus'
import InventoryAdjustDialog from '@/components/InventoryAdjustDialog.vue'

// Stub Teleport so el-dialog's body renders inline and the footer button is
// reachable through the wrapper.
function mountDialog() {
  return mount(InventoryAdjustDialog, {
    props: { modelValue: true, productId: 'SKU-1' },
    global: { plugins: [ElementPlus], stubs: { teleport: true } },
  })
}

async function clickConfirm(wrapper: VueWrapper): Promise<void> {
  const btn = wrapper.findAll('button').find((b) => b.text() === '确定调整')
  await btn!.trigger('click')
  await flushPromises()
}

enableAutoUnmount(afterEach)

describe('InventoryAdjustDialog', () => {
  it('emits the adjustment when delta and reason are provided', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    wrapper.findComponent(ElInputNumber).vm.$emit('update:modelValue', 5)
    await wrapper.find('textarea').setValue('补货入库')
    await flushPromises()
    await clickConfirm(wrapper)

    const emitted = wrapper.emitted('submit')
    expect(emitted).toHaveLength(1)
    expect(emitted![0][0]).toEqual({ delta: 5, reason: '补货入库' })
  })

  it('trims the reason before emitting', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    wrapper.findComponent(ElInputNumber).vm.$emit('update:modelValue', -2)
    await wrapper.find('textarea').setValue('  盘亏  ')
    await flushPromises()
    await clickConfirm(wrapper)

    expect(wrapper.emitted('submit')![0][0]).toEqual({ delta: -2, reason: '盘亏' })
  })

  it('does not emit when the reason is blank', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    wrapper.findComponent(ElInputNumber).vm.$emit('update:modelValue', 5)
    await wrapper.find('textarea').setValue('   ')
    await flushPromises()
    await clickConfirm(wrapper)

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('does not emit when delta is zero', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    wrapper.findComponent(ElInputNumber).vm.$emit('update:modelValue', 0)
    await wrapper.find('textarea').setValue('无效调整')
    await flushPromises()
    await clickConfirm(wrapper)

    expect(wrapper.emitted('submit')).toBeUndefined()
  })
})
