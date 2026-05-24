<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import BrandMark from '@/components/BrandMark.vue'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const scrolled = ref(false)
const popoverOpen = ref(false)
const sheetOpen = ref(false)

function onScroll(): void {
  scrolled.value = window.scrollY > 8
}

// Close the account popover when the user clicks anywhere outside the
// chip + popover region. The attribute selector is the contract so a
// future refactor of the chip's class names doesn't break the test.
function onDocMousedown(e: MouseEvent): void {
  if (!popoverOpen.value) return
  const target = e.target as HTMLElement | null
  if (target && target.closest('[data-account-popover-root]')) return
  popoverOpen.value = false
}

function onKeyDown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    popoverOpen.value = false
    sheetOpen.value = false
  }
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('mousedown', onDocMousedown)
  document.addEventListener('keydown', onKeyDown)
  onScroll()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('mousedown', onDocMousedown)
  document.removeEventListener('keydown', onKeyDown)
})

// Any route change collapses transient UI so the popover/sheet don't
// stay open across navigations.
watch(
  () => route.fullPath,
  () => {
    popoverOpen.value = false
    sheetOpen.value = false
  },
)

const initial = computed(() => {
  const u = auth.currentUser?.username
  if (!u) return '?'
  return u.charAt(0).toUpperCase()
})

// Vue Router 4's automatic .router-link-active only matches when the
// current route's record chain contains the link's target. Our shop
// pages are registered as SIBLING routes — /products and
// /products/:productId have no parent-child relationship — so the
// "Shop" pill would stay un-highlighted on a detail page. /checkout
// also belongs conceptually to the shop flow. Mark Shop active for
// any path in that flow.
const isInProductsSection = computed(
  () =>
    route.path === '/' ||
    route.path.startsWith('/products') ||
    route.path.startsWith('/checkout'),
)

// /payments/:orderNo conceptually belongs to the "My Orders" section.
// Same sibling-routes situation as Shop above.
const isInOrdersSection = computed(
  () => route.path.startsWith('/orders') || route.path.startsWith('/payments'),
)

function toLogin(): void {
  router.push('/login')
}

function toRegister(): void {
  router.push('/register')
}

function onSignOut(): void {
  popoverOpen.value = false
  sheetOpen.value = false
  auth.logout()
  router.push('/login')
}

function togglePopover(): void {
  popoverOpen.value = !popoverOpen.value
}

function toggleSheet(): void {
  sheetOpen.value = !sheetOpen.value
}
</script>

<template>
  <header class="app-header" :class="{ 'app-header--scrolled': scrolled }">
    <div class="app-header__inner">
      <RouterLink class="app-header__brand" to="/products" aria-label="MiniMall home">
        <BrandMark :dot="true" />
      </RouterLink>

      <nav v-if="auth.isLoggedIn" class="app-header__nav" aria-label="Primary">
        <RouterLink
          class="nav-pill"
          :class="{ 'router-link-active': isInProductsSection }"
          to="/products"
        >
          <span>Shop</span>
        </RouterLink>
        <RouterLink
          class="nav-pill"
          :class="{ 'router-link-active': isInOrdersSection }"
          to="/orders"
        >
          <span>My Orders</span>
        </RouterLink>
      </nav>
      <span v-else class="app-header__nav-spacer" />

      <div class="app-header__right">
        <template v-if="auth.isLoggedIn">
          <div class="account" data-account-popover-root>
            <button
              type="button"
              class="account__chip"
              :aria-expanded="popoverOpen"
              aria-haspopup="menu"
              :title="auth.currentUser?.username ?? ''"
              @click="togglePopover"
            >
              <span class="account__initial">{{ initial }}</span>
            </button>
            <Transition name="popover">
              <div v-if="popoverOpen" class="account__popover" role="menu">
                <div class="account__popover-name">
                  {{ auth.currentUser?.username }}
                </div>
                <button
                  type="button"
                  class="account__signout"
                  role="menuitem"
                  @click="onSignOut"
                >
                  Sign out
                </button>
              </div>
            </Transition>
          </div>

          <button
            type="button"
            class="menu-btn"
            :aria-expanded="sheetOpen"
            aria-label="Menu"
            @click="toggleSheet"
          >
            <span class="menu-btn__bar" />
            <span class="menu-btn__bar" />
          </button>
        </template>
        <template v-else>
          <button type="button" class="signin-link" @click="toLogin">
            Sign in
          </button>
          <button type="button" class="ghost-btn" @click="toRegister">
            Create account
          </button>
        </template>
      </div>
    </div>

    <Transition name="sheet">
      <div
        v-if="sheetOpen && auth.isLoggedIn"
        class="sheet"
        role="menu"
        aria-label="Primary navigation"
      >
        <div class="sheet__inner">
          <RouterLink
            class="sheet__link"
            :class="{ 'router-link-active': isInProductsSection }"
            to="/products"
            @click="sheetOpen = false"
          >
            Shop
          </RouterLink>
          <RouterLink
            class="sheet__link"
            :class="{ 'router-link-active': isInOrdersSection }"
            to="/orders"
            @click="sheetOpen = false"
          >
            My Orders
          </RouterLink>
          <div class="sheet__divider" />
          <div class="sheet__user">{{ auth.currentUser?.username }}</div>
          <button type="button" class="sheet__signout" @click="onSignOut">
            Sign out
          </button>
        </div>
      </div>
    </Transition>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: transparent;
  transition: background-color var(--dur-2) var(--ease),
    border-color var(--dur-2) var(--ease);
}

.app-header--scrolled {
  background: rgba(247, 246, 242, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--ink-100);
}

.app-header__inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 24px;
  height: 72px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 24px;
  transition: height var(--dur-2) var(--ease);
}

.app-header--scrolled .app-header__inner {
  height: 56px;
}

.app-header__brand {
  display: inline-flex;
  align-items: center;
}

.app-header__nav {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.app-header__nav-spacer {
  display: block;
}

.nav-pill {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-700);
  border-radius: 999px;
  transition: color var(--dur-2) var(--ease),
    background-color var(--dur-2) var(--ease);
}

.nav-pill:hover {
  color: var(--ink-900);
  background: var(--ink-100);
}

.nav-pill.router-link-active {
  color: var(--ink-900);
}

.nav-pill.router-link-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -8px;
  transform: translateX(-50%);
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--accent-terracotta);
}

.app-header__right {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-self: end;
}

.signin-link {
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-700);
  padding: 8px 4px;
  transition: color var(--dur-2) var(--ease);
}

.signin-link:hover {
  color: var(--ink-900);
}

.ghost-btn {
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-900);
  padding: 8px 16px;
  border: 1px solid var(--ink-300);
  border-radius: var(--radius);
  transition: border-color var(--dur-2) var(--ease),
    background-color var(--dur-2) var(--ease);
}

.ghost-btn:hover {
  border-color: var(--ink-900);
  background: var(--ink-100);
}

.account {
  position: relative;
}

.account__chip {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--canvas-darker);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background-color var(--dur-2) var(--ease);
}

.account__chip:hover {
  background: var(--ink-100);
}

.account__initial {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-900);
  line-height: 1;
}

.account__popover {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  min-width: 200px;
  background: var(--surface);
  border: 1px solid var(--ink-100);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-2);
  padding: 8px;
  z-index: 60;
}

.account__popover-name {
  padding: 8px 12px 12px;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
  border-bottom: 1px solid var(--ink-100);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account__signout {
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--ink-700);
  border-radius: var(--radius-sm);
  transition: background-color var(--dur-2) var(--ease),
    color var(--dur-2) var(--ease);
}

.account__signout:hover {
  background: var(--ink-100);
  color: var(--ink-900);
}

.menu-btn {
  display: none;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 5px;
  transition: background-color var(--dur-2) var(--ease);
}

.menu-btn:hover {
  background: var(--ink-100);
}

.menu-btn__bar {
  display: block;
  width: 18px;
  height: 1.5px;
  background: var(--ink-900);
  border-radius: 999px;
}

.sheet {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: var(--surface);
  border-top: 1px solid var(--ink-100);
  border-bottom: 1px solid var(--ink-100);
  box-shadow: var(--shadow-1);
}

.sheet__inner {
  padding: 12px 16px 20px;
  display: flex;
  flex-direction: column;
}

.sheet__link {
  display: block;
  padding: 12px 4px;
  font-family: var(--font-sans);
  font-size: 16px;
  font-weight: 500;
  color: var(--ink-700);
}

.sheet__link.router-link-active {
  color: var(--ink-900);
}

.sheet__divider {
  height: 1px;
  background: var(--ink-100);
  margin: 8px 0;
}

.sheet__user {
  padding: 12px 4px 4px;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--ink-500);
}

.sheet__signout {
  display: block;
  padding: 12px 4px;
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--ink-700);
  width: 100%;
  text-align: left;
}

.popover-enter-active,
.popover-leave-active {
  transition: opacity var(--dur-2) var(--ease),
    transform var(--dur-2) var(--ease);
}

.popover-enter-from,
.popover-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity var(--dur-2) var(--ease),
    transform var(--dur-2) var(--ease);
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 639px) {
  .app-header__inner {
    padding: 0 16px;
    gap: 12px;
  }

  .app-header__nav {
    display: none;
  }

  .account {
    display: none;
  }

  .menu-btn {
    display: inline-flex;
  }
}
</style>
