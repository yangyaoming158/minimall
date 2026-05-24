// Deterministic product-cover spec. Given a (productId, name) pair, returns a
// fully-resolved description of what the ProductCover.vue component should
// render: a palette, a geometry primitive, a rotation, a small offset, and an
// initial mark. The same inputs ALWAYS produce the same output — no Math.random,
// no Date.now — so the same product looks identical on the list, the detail
// page, the order row, and the checkout summary.

// 32-bit FNV-1a hash. No deps, ~10 lines, stable across browsers + Node.
export function fnv1a(input: string): number {
  let hash = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  return hash >>> 0
}

export type PaletteName =
  | 'Sand'
  | 'Olive'
  | 'Slate'
  | 'Clay'
  | 'Moss'
  | 'Smoke'
  | 'Linen'
  | 'Mist'

export interface Palette {
  name: PaletteName
  base: string
  baseDark: string
  accent: string
  accentDeep: string
}

// 8 hand-tuned palettes aligned with the brief's §2 visual system.
// Each palette: a soft warm/cool base + a slightly darker version of itself
// (for the background gradient) + a single saturated accent + a deeper accent
// shade (for the initial mark and composite geometry secondary fill).
export const PALETTES: Palette[] = [
  { name: 'Sand',  base: '#E8DCC4', baseDark: '#D8C9AE', accent: '#B5552F', accentDeep: '#8A3F22' },
  { name: 'Olive', base: '#D8DCC0', baseDark: '#C6CBAA', accent: '#5A6B3A', accentDeep: '#3F4D27' },
  { name: 'Slate', base: '#D6DAE0', baseDark: '#C2C7CF', accent: '#3E4B5E', accentDeep: '#2A3445' },
  { name: 'Clay',  base: '#E5C8B6', baseDark: '#D2B19D', accent: '#8B4A2F', accentDeep: '#653320' },
  { name: 'Moss',  base: '#C8D4C0', baseDark: '#B3C2AA', accent: '#3F5A3A', accentDeep: '#2B4127' },
  { name: 'Smoke', base: '#D5D3CE', baseDark: '#BFBDB7', accent: '#52514B', accentDeep: '#3A3A36' },
  { name: 'Linen', base: '#EFE9DD', baseDark: '#DDD5C3', accent: '#7A6B4F', accentDeep: '#574A35' },
  { name: 'Mist',  base: '#D9DDDD', baseDark: '#C3C9C9', accent: '#4B5C5C', accentDeep: '#324040' },
]

export type GeometryKind =
  | 'disc'
  | 'arc'
  | 'lines'
  | 'rings'
  | 'diamond'
  | 'composite'

export const GEOMETRIES: GeometryKind[] = [
  'disc',
  'arc',
  'lines',
  'rings',
  'diamond',
  'composite',
]

export interface CoverSpec {
  palette: Palette
  geometry: GeometryKind
  initial: string
  rotation: number // multiple of 15 in [0, 360)
  offsetX: number // fraction of viewBox width, ~[-0.2, 0.2]
  offsetY: number // fraction of viewBox height, ~[-0.2, 0.2]
  seed: number // raw 32-bit unsigned hash, exposed so consumers can derive stable ids
}

// CJK Unified Ideographs (main + Extension A) + CJK Compatibility Ideographs.
// Covers all everyday Simplified/Traditional Chinese product names.
const CJK_RE = /[㐀-鿿豈-﫿]/u

// Returns the visual character to stamp on the cover. CJK names → first CJK
// glyph; Latin/digit names → up to 2 alphanumeric characters uppercased;
// empty/whitespace/null → '?'. The character is purely decorative — never
// used for identification or routing.
export function extractInitial(name: string | null | undefined): string {
  if (!name) return '?'
  const trimmed = name.trim()
  if (!trimmed) return '?'
  for (const ch of trimmed) {
    if (CJK_RE.test(ch)) return ch
  }
  const latin = trimmed.replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase()
  return latin || '?'
}

// Resolve the full cover spec for a product. The productId is the primary
// determinism input; the name only feeds the initial mark (and adds entropy
// so two products with identical names but different ids still differ).
export function coverFor(
  productId: number | string | null | undefined,
  name?: string | null,
): CoverSpec {
  const idStr = productId == null ? '' : String(productId)
  const nameStr = name ?? ''
  const hash = fnv1a(`${idStr}::${nameStr}`)

  const palette = PALETTES[hash % PALETTES.length]
  const geometry = GEOMETRIES[(hash >>> 8) % GEOMETRIES.length]
  const rotation = ((hash >>> 16) % 24) * 15
  const offsetX = (((hash >>> 4) & 0xff) / 255 - 0.5) * 0.4
  const offsetY = (((hash >>> 12) & 0xff) / 255 - 0.5) * 0.4
  const initial = extractInitial(nameStr)

  return { palette, geometry, initial, rotation, offsetX, offsetY, seed: hash }
}
