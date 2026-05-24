import { describe, expect, it } from 'vitest'
import {
  GEOMETRIES,
  PALETTES,
  coverFor,
  extractInitial,
  fnv1a,
} from '@/utils/cover'

describe('fnv1a', () => {
  it('is deterministic for the same input', () => {
    expect(fnv1a('abc')).toBe(fnv1a('abc'))
    expect(fnv1a('P-1024::香薰蜡烛')).toBe(fnv1a('P-1024::香薰蜡烛'))
  })

  it('differs for different inputs', () => {
    expect(fnv1a('abc')).not.toBe(fnv1a('abd'))
    expect(fnv1a('abc')).not.toBe(fnv1a('cba'))
  })

  it('returns an unsigned 32-bit integer', () => {
    const samples = ['', 'a', 'hello world', 'P-1024', '香薰蜡烛']
    for (const s of samples) {
      const h = fnv1a(s)
      expect(Number.isInteger(h)).toBe(true)
      expect(h).toBeGreaterThanOrEqual(0)
      expect(h).toBeLessThanOrEqual(0xffffffff)
    }
  })
})

describe('extractInitial', () => {
  it('returns the first CJK glyph when one is present', () => {
    expect(extractInitial('香薰蜡烛')).toBe('香')
    expect(extractInitial('  橄榄油 ')).toBe('橄')
    expect(extractInitial('Premium 茶具 set')).toBe('茶')
  })

  it('returns up to 2 alphanumeric chars uppercased for Latin names', () => {
    expect(extractInitial('aroma candle')).toBe('AR')
    expect(extractInitial('olive')).toBe('OL')
    expect(extractInitial('a')).toBe('A')
  })

  it('strips non-alphanumeric chars before slicing', () => {
    expect(extractInitial('3-pack bundle')).toBe('3P')
    expect(extractInitial('  !!premium')).toBe('PR')
  })

  it('falls back to ? for empty / whitespace / nullish input', () => {
    expect(extractInitial('')).toBe('?')
    expect(extractInitial('   ')).toBe('?')
    expect(extractInitial(null)).toBe('?')
    expect(extractInitial(undefined)).toBe('?')
    expect(extractInitial('!!!')).toBe('?')
  })
})

describe('coverFor', () => {
  it('is deterministic for the same (productId, name) pair', () => {
    const a = coverFor('P-1024', '香薰蜡烛')
    const b = coverFor('P-1024', '香薰蜡烛')
    expect(a).toEqual(b)
  })

  it('produces the same spec for numeric and string id forms', () => {
    expect(coverFor(42, 'foo')).toEqual(coverFor('42', 'foo'))
  })

  it('always picks a known palette across a wide sample', () => {
    const names = PALETTES.map((p) => p.name)
    for (let i = 0; i < 100; i++) {
      const c = coverFor(i, `Product ${i}`)
      expect(names).toContain(c.palette.name)
    }
  })

  it('always picks a known geometry across a wide sample', () => {
    for (let i = 0; i < 100; i++) {
      const c = coverFor(i, `Product ${i}`)
      expect(GEOMETRIES).toContain(c.geometry)
    }
  })

  it('rotation is a multiple of 15 in [0, 360)', () => {
    for (let i = 0; i < 100; i++) {
      const { rotation } = coverFor(i, `Product ${i}`)
      expect(rotation % 15).toBe(0)
      expect(rotation).toBeGreaterThanOrEqual(0)
      expect(rotation).toBeLessThan(360)
    }
  })

  it('offsets stay within [-0.2, 0.2]', () => {
    for (let i = 0; i < 100; i++) {
      const { offsetX, offsetY } = coverFor(i, `Product ${i}`)
      expect(offsetX).toBeGreaterThanOrEqual(-0.2)
      expect(offsetX).toBeLessThanOrEqual(0.2)
      expect(offsetY).toBeGreaterThanOrEqual(-0.2)
      expect(offsetY).toBeLessThanOrEqual(0.2)
    }
  })

  it('handles null / undefined / empty ids without throwing', () => {
    expect(() => coverFor(null, null)).not.toThrow()
    expect(() => coverFor(undefined, undefined)).not.toThrow()
    expect(() => coverFor('', '')).not.toThrow()
    expect(coverFor('', '').initial).toBe('?')
  })

  it('different productIds yield distinct seeds', () => {
    expect(coverFor('P-1', 'x').seed).not.toBe(coverFor('P-2', 'x').seed)
  })

  it('different names with the same productId yield distinct seeds', () => {
    expect(coverFor('P-1', 'foo').seed).not.toBe(coverFor('P-1', 'bar').seed)
  })

  it('uses the initial derived from the name', () => {
    expect(coverFor('P-1', '香薰').initial).toBe('香')
    expect(coverFor('P-2', 'olive oil').initial).toBe('OL')
    expect(coverFor('P-3', '').initial).toBe('?')
  })
})
