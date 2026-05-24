import { mount, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import QuantityStepper from '@/components/atoms/QuantityStepper.vue'

enableAutoUnmount(afterEach)

describe('QuantityStepper', () => {
  it('emits an incremented integer on + click', async () => {
    const w = mount(QuantityStepper, {
      props: { modelValue: 3, min: 1, max: 10 },
    })
    await w.find('.qty__btn--inc').trigger('click')
    const emits = w.emitted('update:modelValue')
    expect(emits).toBeTruthy()
    expect(emits![0]).toEqual([4])
  })

  it('emits a decremented integer on − click', async () => {
    const w = mount(QuantityStepper, {
      props: { modelValue: 5, min: 1, max: 10 },
    })
    await w.find('.qty__btn--dec').trigger('click')
    expect(w.emitted('update:modelValue')![0]).toEqual([4])
  })

  it('disables the + button and refuses to emit when modelValue equals max', async () => {
    const w = mount(QuantityStepper, {
      props: { modelValue: 10, min: 1, max: 10 },
    })
    const incBtn = w.find('.qty__btn--inc')
    expect((incBtn.element as HTMLButtonElement).disabled).toBe(true)
    await incBtn.trigger('click')
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })

  it('disables the − button and refuses to emit when modelValue equals min', async () => {
    const w = mount(QuantityStepper, {
      props: { modelValue: 1, min: 1, max: 10 },
    })
    const decBtn = w.find('.qty__btn--dec')
    expect((decBtn.element as HTMLButtonElement).disabled).toBe(true)
    await decBtn.trigger('click')
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })

  it('clamps a typed value above max via the input event', async () => {
    const w = mount(QuantityStepper, {
      props: { modelValue: 3, min: 1, max: 10 },
    })
    const input = w.find('.qty__input')
    await input.setValue('99')
    const emits = w.emitted('update:modelValue')
    expect(emits).toBeTruthy()
    expect(emits![emits!.length - 1]).toEqual([10])
  })

  it('reverts to min when the input is cleared and then blurred', async () => {
    const w = mount(QuantityStepper, {
      props: { modelValue: 3, min: 2, max: 10 },
    })
    const input = w.find('.qty__input')
    ;(input.element as HTMLInputElement).value = ''
    await input.trigger('blur')
    const emits = w.emitted('update:modelValue')
    expect(emits).toBeTruthy()
    expect(emits![emits!.length - 1]).toEqual([2])
  })

  it('disables both step buttons and the input when disabled prop is true', () => {
    const w = mount(QuantityStepper, {
      props: { modelValue: 3, min: 1, max: 10, disabled: true },
    })
    expect((w.find('.qty__btn--dec').element as HTMLButtonElement).disabled).toBe(true)
    expect((w.find('.qty__btn--inc').element as HTMLButtonElement).disabled).toBe(true)
    expect((w.find('.qty__input').element as HTMLInputElement).disabled).toBe(true)
  })
})
