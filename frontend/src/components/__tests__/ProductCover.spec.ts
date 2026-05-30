import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ProductCover from '@/components/ProductCover.vue'

describe('ProductCover - imageUrl with placeholder fallback', () => {
  it('renders the real image when imageUrl is provided', () => {
    const wrapper = mount(ProductCover, {
      props: { productId: 'SKU-1', name: 'Widget', imageUrl: 'https://cdn.example.com/x.png' },
    })

    const img = wrapper.find('img.cover__img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://cdn.example.com/x.png')
    expect(wrapper.find('svg').exists()).toBe(false)
  })

  it('renders the generated SVG placeholder when imageUrl is absent', () => {
    const wrapper = mount(ProductCover, {
      props: { productId: 'SKU-1', name: 'Widget', imageUrl: null },
    })

    expect(wrapper.find('img.cover__img').exists()).toBe(false)
    expect(wrapper.find('svg').exists()).toBe(true)
  })

  it('falls back to the SVG placeholder when the image fails to load', async () => {
    const wrapper = mount(ProductCover, {
      props: { productId: 'SKU-1', name: 'Widget', imageUrl: 'https://cdn.example.com/broken.png' },
    })

    await wrapper.find('img.cover__img').trigger('error')

    expect(wrapper.find('img.cover__img').exists()).toBe(false)
    expect(wrapper.find('svg').exists()).toBe(true)
  })
})
