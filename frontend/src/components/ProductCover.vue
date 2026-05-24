<script setup lang="ts">
import { computed } from 'vue'
import { coverFor, type CoverSpec } from '@/utils/cover'

type Aspect = '4:5' | '1:1' | '16:9'
type Grade = 'list' | 'detail'
type Size = 'sm' | 'md' | 'lg' | 'full'

const props = withDefaults(
  defineProps<{
    productId: number | string | null | undefined
    name?: string | null
    aspect?: Aspect
    grade?: Grade
    size?: Size
  }>(),
  { aspect: '4:5', grade: 'list', size: 'md' },
)

const VIEWBOX: Record<Aspect, { w: number; h: number }> = {
  '4:5': { w: 400, h: 500 },
  '1:1': { w: 400, h: 400 },
  '16:9': { w: 400, h: 225 },
}

const ASPECT_CSS: Record<Aspect, string> = {
  '4:5': '4 / 5',
  '1:1': '1 / 1',
  '16:9': '16 / 9',
}

const SIZE_PX: Record<Size, string> = {
  sm: '64px',
  md: '160px',
  lg: '280px',
  full: '100%',
}

const spec = computed<CoverSpec>(() => coverFor(props.productId, props.name))
const vb = computed(() => VIEWBOX[props.aspect])

const cx = computed(() => vb.value.w / 2 + spec.value.offsetX * vb.value.w)
const cy = computed(() => vb.value.h / 2 + spec.value.offsetY * vb.value.h)
const major = computed(() => Math.min(vb.value.w, vb.value.h))

const gradId = computed(() => `pcv-grad-${spec.value.seed}`)
const haloId = computed(() => `pcv-halo-${spec.value.seed}`)
const noiseId = computed(() => `pcv-noise-${spec.value.seed}`)

const isDetail = computed(() => props.grade === 'detail')

const geomTransform = computed(
  () => `rotate(${spec.value.rotation} ${cx.value} ${cy.value})`,
)

const figureStyle = computed(() => ({
  width: SIZE_PX[props.size],
  aspectRatio: ASPECT_CSS[props.aspect],
}))

const ariaLabel = computed(() => props.name || 'Product cover')
</script>

<template>
  <figure
    class="cover"
    :class="[`cover--${grade}`]"
    :style="figureStyle"
    :aria-label="ariaLabel"
  >
    <svg
      :viewBox="`0 0 ${vb.w} ${vb.h}`"
      preserveAspectRatio="xMidYMid slice"
      role="img"
      :aria-label="ariaLabel"
    >
      <defs>
        <linearGradient :id="gradId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" :stop-color="spec.palette.base" />
          <stop offset="100%" :stop-color="spec.palette.baseDark" />
        </linearGradient>

        <radialGradient
          v-if="isDetail"
          :id="haloId"
          cx="78%"
          cy="22%"
          r="80%"
        >
          <stop offset="0%" :stop-color="spec.palette.accent" stop-opacity="0.2" />
          <stop offset="60%" :stop-color="spec.palette.accent" stop-opacity="0" />
        </radialGradient>

        <filter v-if="isDetail" :id="noiseId" x="0" y="0" width="100%" height="100%">
          <feTurbulence type="fractalNoise" baseFrequency="1.2" numOctaves="2" seed="2" />
          <feColorMatrix
            values="0 0 0 0 0
                    0 0 0 0 0
                    0 0 0 0 0
                    0 0 0 0.05 0"
          />
        </filter>
      </defs>

      <rect :width="vb.w" :height="vb.h" :fill="`url(#${gradId})`" />

      <g :transform="geomTransform">
        <circle
          v-if="spec.geometry === 'disc'"
          :cx="cx"
          :cy="cy"
          :r="major * 0.36"
          :fill="spec.palette.accent"
          opacity="0.82"
        />

        <path
          v-else-if="spec.geometry === 'arc'"
          :d="`M ${cx - major * 0.42} ${cy} A ${major * 0.42} ${major * 0.42} 0 0 1 ${cx + major * 0.42} ${cy} Z`"
          :fill="spec.palette.accent"
          opacity="0.85"
        />

        <g
          v-else-if="spec.geometry === 'lines'"
          :fill="spec.palette.accent"
          opacity="0.78"
        >
          <rect
            :x="cx - major * 0.45"
            :y="cy - major * 0.18"
            :width="major * 0.9"
            :height="major * 0.03"
          />
          <rect
            :x="cx - major * 0.45"
            :y="cy - major * 0.02"
            :width="major * 0.9"
            :height="major * 0.03"
          />
          <rect
            :x="cx - major * 0.45"
            :y="cy + major * 0.14"
            :width="major * 0.9"
            :height="major * 0.03"
          />
        </g>

        <g
          v-else-if="spec.geometry === 'rings'"
          :stroke="spec.palette.accent"
          fill="none"
          opacity="0.82"
        >
          <circle :cx="cx" :cy="cy" :r="major * 0.4" stroke-width="2.5" />
          <circle :cx="cx" :cy="cy" :r="major * 0.28" stroke-width="2.5" />
          <circle :cx="cx" :cy="cy" :r="major * 0.16" stroke-width="2.5" />
        </g>

        <rect
          v-else-if="spec.geometry === 'diamond'"
          :x="cx - major * 0.28"
          :y="cy - major * 0.28"
          :width="major * 0.56"
          :height="major * 0.56"
          :transform="`rotate(45 ${cx} ${cy})`"
          :fill="spec.palette.accent"
          opacity="0.82"
        />

        <g
          v-else-if="spec.geometry === 'composite'"
          :fill="spec.palette.accent"
          opacity="0.86"
        >
          <polygon
            :points="`${cx - major * 0.32},${cy + major * 0.22} ${cx + major * 0.08},${cy + major * 0.22} ${cx - major * 0.12},${cy - major * 0.18}`"
          />
          <circle
            :cx="cx + major * 0.22"
            :cy="cy - major * 0.06"
            :r="major * 0.11"
            :fill="spec.palette.accentDeep"
          />
        </g>
      </g>

      <rect
        v-if="isDetail"
        :width="vb.w"
        :height="vb.h"
        :fill="`url(#${haloId})`"
      />
      <rect
        v-if="isDetail"
        :width="vb.w"
        :height="vb.h"
        :filter="`url(#${noiseId})`"
        opacity="0.55"
      />

      <text
        :x="vb.w * 0.08"
        :y="vb.h * 0.92"
        :fill="spec.palette.accentDeep"
        :font-size="vb.h * 0.18"
        font-family="'Inter Tight', Inter, system-ui, sans-serif"
        font-weight="700"
        opacity="0.92"
      >
        {{ spec.initial }}
      </text>
    </svg>
  </figure>
</template>

<style scoped>
.cover {
  display: block;
  margin: 0;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--canvas-darker);
  position: relative;
  isolation: isolate;
}

.cover svg {
  display: block;
  width: 100%;
  height: 100%;
}

.cover--detail {
  border-radius: var(--radius-lg);
}
</style>
