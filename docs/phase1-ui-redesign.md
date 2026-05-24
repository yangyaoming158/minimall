# MiniMall Phase 1 — Customer Frontend UI/UX Redesign Brief

> **Status:** Design contract (not yet implemented).
> **Scope:** Visual & interaction overhaul of the existing customer frontend.
> **Hard boundaries:** No new business modules. No API contract changes. No backend changes. No new product fields. No admin/RBAC/cart/address/coupon/real-payment/refund.
> **Owner directive:** "PRD decides feature boundaries; visual expression is yours to drive. You may aggressively improve UI, but cannot add business modules."

This document is **self-binding**: during implementation I MUST honor the rules in §10. If a step tempts me to "just override Element Plus colors", that step is wrong — re-read §0 and §10.

---

## 0. Anti-patterns to refuse (the "do not regress" list)

These are traps I caught myself walking into during the first design pass. Every implementation commit must avoid them.

1. **"Override EP CSS variables and call it done."** Recoloring `el-button` / `el-tag` / `el-result` is NOT a redesign. The shape language of those components is admin-form-shaped. Replace, don't repaint.
2. **`v-loading` gray spinner.** Forbidden on every customer-facing surface. Use structural skeletons.
3. **One 1200px container everywhere.** Admin tic. Different pages need different breathing rhythms (see §5).
4. **"Label : value" rows for product / order / payment info.** That's a form, not a storefront. Use editorial layout: hero, headline, prose, summary.
5. **Red price (#f56c6c).** This is EP's danger color. Price uses ink-900, not red.
6. **`type="primary"` blue button.** Storefront CTAs are ink-900 (black) filled. No EP blue anywhere user-facing.
7. **`el-result` / `el-empty`.** The cloud icon and cardboard box scream admin. Replace with custom EmptyState / ErrorState.
8. **`el-tag` for status.** Replace with `DotStatus` (dot + label) or `Pill` (soft tint + label).
9. **Skipping motion** because "we can polish later." Page fade-in, button press scale, card hover lift, skeleton shimmer, payment-success ceremony — these are core to the storefront feel, not polish.
10. **Generating "filler" UI** to look fancy. Decorative variant thumbnails on the product detail are OK because they reinforce identity; carousels with fake data, fake reviews, fake "trending" sections are NOT — they fabricate features the PRD doesn't have.

---

## 1. Design intent

**Style name:** *Soft Minimal Storefront* — boutique DTC voice, restrained, breathing, slightly warm. Reference vibe: Apple Store product pages, Aesop, Everlane, Stripe Checkout.

**Voice keywords:** quiet, considered, deliberate whitespace, ink + paper, product imagery carries the color, motion is gentle and brief.

**One-sentence test:** if a casual visitor cannot tell within 1 second that this is a *shop* and not a *dashboard*, the design has failed.

---

## 2. Visual system

### 2.1 Color

Single warm accent (terracotta) used **sparingly** as brand anchor. All other surfaces are ink + warm paper. Color carried by product covers, not chrome.

| Token | Hex | Usage |
|---|---|---|
| `--ink-900` | `#0F1115` | Headlines, primary CTA fill, price |
| `--ink-700` | `#2A2F3A` | Body, secondary CTA text |
| `--ink-500` | `#6B7180` | Captions, helper text, mono order numbers |
| `--ink-300` | `#B8BDC7` | Placeholders, finished steps, decorative |
| `--ink-100` | `#ECEEF2` | Hairlines, dividers |
| `--canvas` | `#F7F6F2` | Page background (warm paper) |
| `--canvas-darker` | `#EFEDE7` | Decorative blobs behind empty states |
| `--surface` | `#FFFFFF` | Card body |
| `--accent-terracotta` | `#B5552F` | Brand dot, current step, pending warmth, in-stock dot, hero ornament |
| `--accent-terracotta-soft` | `#F2E4DB` | Pending pill background, soft attention |
| `--success` | `#2F8F5C` | Paid state, success ceremony tick |
| `--success-soft` | `#E1EFE6` | Paid pill background |
| `--warn` | `#B07A1A` | Stock-low, "almost gone" emphasis |
| `--danger` | `#B23A2A` | Errors (calmer than EP `#f56c6c`) |
| `--danger-soft` | `#F4E2DE` | Inline error chip background |

**Decision:** Price is `--ink-900`, NOT red. Tabular figures. The boutique cue is type weight, not color.

### 2.2 Type

| Token | Family / weight / size / line-height | Usage |
|---|---|---|
| `display-xl` | Inter Tight / 700 / 48 / 1.1, letter-spacing -0.02em | Login hero, payment amount, 404 numeral |
| `display` | Inter Tight / 600 / 32 / 1.15 | Product list headline, payment success title |
| `h1` | Inter / 600 / 24 / 1.3 | Page titles |
| `h2` | Inter / 600 / 18 / 1.4 | Section titles inside pages |
| `body` | Inter / 400 / 14 / 1.7 | Paragraphs, descriptions |
| `body-strong` | Inter / 500 / 14 / 1.6 | Inline labels |
| `caption` | Inter / 400 / 12 / 1.5 | Meta, order time, hints |
| `mono-sm` | ui-monospace / 400 / 12 / 1.5 | Order numbers, product IDs |
| `price-lg` | Inter / 700 / 32 / 1, tabular-nums | Detail page price |
| `price-md` | Inter / 600 / 20 / 1, tabular-nums | List & order card price |
| `amount-display` | Inter Tight / 700 / 56 / 1, tabular-nums | Payment page amount block |
| `brand` | Inter Tight / 600 / 18, letter-spacing +0.04em | "MiniMall" wordmark |

**zh fallback:** `"PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif` after the Inter family.

**Decision:** Numerals use `font-variant-numeric: tabular-nums` everywhere money or counts appear, so columns of prices and order totals align cleanly.

### 2.3 Spacing / radius / shadow / motion

```
--space:     4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 / 64 / 96
--radius-sm: 6   (chips)
--radius:    10  (buttons, inputs)
--radius-md: 12  (small cards)
--radius-lg: 16  (product cards, panels)
--radius-xl: 24  (hero, payment amount block)

--shadow-1: 0 1px 2px rgba(15,17,21,.04)
--shadow-2: 0 4px 16px rgba(15,17,21,.05)
--shadow-3: 0 12px 32px rgba(15,17,21,.08)   (hover lift)
--shadow-press: 0 0 0 4px rgba(15,17,21,.06) (focus ring)

--ease:    cubic-bezier(.2,.6,.2,1)
--ease-in: cubic-bezier(.4,0,.6,1)
--dur-1:   120ms   (press)
--dur-2:   220ms   (hover, page fade)
--dur-3:   420ms   (number tween, hero reveal)
--dur-4:   800ms   (success ceremony)
```

### 2.4 Iconography

No `el-icon` use outside form internals. Custom inline SVGs, 1.5–2px stroke, ink-700, 20px default. Stripe / Heroicons / Lucide-style. Round line-cap. No filled icons except the success tick.

---

## 3. Component architecture

### 3.1 Keep vs replace (storefront surface)

| Surface | Decision |
|---|---|
| `el-form` / `el-form-item` validation engine | **Keep** (form rules work well, no visual chrome on the user) |
| `ElMessage` toast | **Keep** (top-right, brief, neutral) |
| `ElMessageBox.confirm` (cancel order dialog) | **Keep** (modal chrome is fine; restyle text only) |
| `el-input` (under the hood) | **Wrap** inside our own `<Field>` so the visible shell is ours |
| `el-button` | **Replace** with `<Button variant="primary | ghost | text">` |
| `el-tag` | **Replace** with `<Pill>` and `<DotStatus>` |
| `el-result` | **Replace** with `<EmptyState>` / `<ErrorState>` |
| `el-empty` | **Replace** with `<EmptyState>` |
| `el-alert` | **Replace** with `<Notice>` (hairline-bordered, no harsh fill) |
| `el-loading` directive | **Remove**. Replace with skeleton components per page |
| `el-pagination` | **Replace** with `<Pager>` (text-link prev/next + page chips) |
| `el-input-number` | **Replace** with `<QuantityStepper>` |
| `el-select` | **Replace** on storefront filters with `<PillGroup>` tab pills |
| `el-divider` | **Replace** with `<Hairline />` (1px `--ink-100`) |
| `el-row` / `el-col` grid | **Replace** with CSS grid (`.grid-cols-*` utility classes) |

### 3.2 Atom components (new)

```
<Button variant="primary | ghost | text" size="sm | md | lg" :loading>
<Pill tone="neutral | terracotta | success | warn | danger" soft>
<DotStatus tone="..." label />
<PriceText :amount :size="md | lg | xl" />
<Hairline />
<Field label hint error>      // wraps el-input internals
<QuantityStepper v-model :min :max :disabled />
<Skeleton :width :height :radius shimmer />
<SkeletonText :lines />
<Notice tone="info | warn | danger" title>
<Pager :total :pageSize :current @change />
```

### 3.3 Molecule components (new)

```
<AppHeader />            // brand + nav + account chip, scroll-aware
<AppFooter />            // quiet brand line at canvas color
<PageContainer width="xs | sm | md | lg | xl">
<ProductCover :productId :name :size :variant?="0|1|2|3" />
<ProductCard :product />
<ProductHero :product />            // detail page left column
<PurchasePanel :product :inventory />  // detail page right column, sticky
<OrderItemRow :item />               // checkout & order detail
<OrderSummary :items :total />
<OrderCard :order />                 // orders list
<OrderStepper :status />             // 下单 → 已支付 → 完成
<AmountDisplay :amount :animateOnMount />
<PaymentResultPanel :state :payment />
<EmptyState title sub>
<ErrorState title sub @retry />
<AuthHero side="left | right">       // login/register side panel
<BrandMark />                        // wordmark + dot
<CountdownChip :until />              // pending payment expiry
```

### 3.4 Skeleton components (per page)

```
<ProductCardSkeleton />
<ProductListSkeleton :count />
<ProductDetailSkeleton />
<CheckoutSkeleton />
<OrderCardSkeleton />
<OrdersListSkeleton :count />
<OrderDetailSkeleton />
<PaymentSkeleton />
```

All skeletons use `<Skeleton>` atom with shimmer; structural shapes match the real layout's footprint so the page does not jump when content loads.

---

## 4. Product cover system (no-backend-image strategy)

The single most important visual lever. Must look intentional, not "placeholder". Used on: list card, detail hero, detail variant thumbnails, checkout item row, order card, order detail row, payment receipt mini-thumb.

### 4.1 Determinism

Cover is a pure function of `(productId, name)`:

```
hash       = fnv1a(productId)
paletteIdx = hash      % 8
geometryIdx= (hash>>3) % 6
rotation   = (hash>>6) % 360
hueShift   = (hash>>9) % 24 - 12     // ±12° within palette
initial    = firstCJKCharOrLatinInitial(name)
```

Same product → identical cover on every page, forever.

### 4.2 Palette set (8 boutique palettes)

Each palette is a 2-stop gradient. Hand-tuned to read as boutique product photography, never neon.

```
P0  Sand          #E9DECE → #C8B8A2
P1  Olive         #D2D4B6 → #97A07A
P2  Slate         #C3CCD3 → #7E8A93
P3  Clay          #E7C9B2 → #B58A6F
P4  Moss          #C7D2BD → #7D8C70
P5  Smoke         #DCDADA → #9A9695
P6  Linen         #EBE2D6 → #C3B59E
P7  Mist          #CFD7DC → #8E99A1
```

All palettes are low-chroma. No saturated colors. The product list's overall color impression should feel like a stack of folded fabrics.

### 4.3 Geometry layer (6 SVG primitives)

Inline SVG, `mix-blend-mode: soft-light` on the gradient.

```
G0 Big circle, 70% width, off-center top-right
G1 Long horizontal capsule, mid-height
G2 Half-arc spanning the bottom
G3 Two overlapping circles, lower-right
G4 Diagonal stripe, 8° tilt
G5 Dot grid, 6×4
```

Each geometry has a stable `rotate(rotation deg)` applied. Stroke 1.5px or filled `rgba(255,255,255,.35)`.

### 4.4 Initial mark

- Single CJK char (`无线充电器` → `无`) OR Latin first 1–2 chars (`iPhone 15 Pro` → `iP`).
- Color `rgba(255,255,255,.78)`.
- Font: Inter Tight, 600, sized to ~38% of cover height.
- Position: bottom-left, padding inset (cover height × 0.1).
- Letter-spacing -0.02em.

### 4.5 Aspect ratios by placement

| Placement | AR | Notes |
|---|---|---|
| List card | **4:5** | Taller than square; boutique vertical feel |
| Detail hero | **4:5** | Same AR, larger; "detail-grade" rendering (see 4.6) |
| Detail variant thumbnails (×3) | 1:1 | Decorative, swap hero on click (re-seed geometry) |
| Checkout item row | 1:1, 80px | Compact |
| Order card | 1:1, 64px | Compact |
| Order detail item row | 1:1, 72px | Slightly larger than list |
| Payment receipt mini-thumb | 1:1, 48px | Smallest |

### 4.6 Detail-grade rendering

The detail hero cover renders at **higher fidelity** than the list cover, so it feels like "the big product shot" instead of "the big version of the thumbnail":

- 2 geometry layers stacked instead of 1.
- Subtle film-grain noise overlay (1.5kB inline SVG noise pattern, `opacity: .08`).
- Inner shadow (`inset 0 -40px 80px rgba(15,17,21,.06)`) — gives "studio lit" feel.
- Initial mark is 20% smaller and uses `mix-blend-mode: overlay` instead of plain alpha.

### 4.7 Variant thumbnails (decorative, ≤3)

- Below the hero, three 56×56 thumbnails, 1:1.
- Each uses the same palette but a *different* geometry index (`(geometryIdx+1) % 6`, `+2`, `+3`).
- Clicking a thumbnail swaps the hero's geometry with a 220ms cross-fade. **No business meaning** — this is a delight detail to make the detail page feel like a proper product page. Tracked as visual only; never wired to inventory or API.

### 4.8 Off-shelf overlay

When `product.status === 'OFF_SHELF'`:
- Cover desaturates: `filter: grayscale(.6) opacity(.85)`.
- A small `Pill tone="neutral" soft` in the cover's top-right with text "已下架".

---

## 5. Page-by-page specification

Container widths are **page-specific** by design:

| Page | Max width |
|---|---|
| Product list | 1280 |
| Product detail | 1200 |
| Orders list | 920 |
| Order detail | 720 |
| Checkout | 560 |
| Payment | 480 |
| Login / Register | 1200 (two-column) |
| 404 / 403 | full |

### 5.1 AppShell (Header + Footer + global)

**Header:**
- Default: 72 high, transparent over canvas, no border.
- On scroll > 8px: collapses to 56, adds `border-bottom: 1px solid var(--ink-100)`, `backdrop-filter: blur(8px)`, `background: rgba(247,246,242,.85)`.
- Left: `<BrandMark>` — "MiniMall" wordmark + 6px terracotta dot before the M.
- Center: nav pills "Shop" / "My Orders" (logged-in only). Active section has a 4px terracotta dot below the pill. No underline.
- Right (logged-in): account chip — 28px circle with the user's first initial in ink-900 on `--canvas-darker`. Click opens a small popover (custom, not EP) with "Sign out".
- Right (logged-out): "Sign in" text button + "Create account" ghost button.
- Mobile (< 640): center nav becomes a "Menu" button that opens a slide-down sheet.

**Footer:**
- 80 high, canvas color, top hairline.
- Left: small `BrandMark` (smaller wordmark, no terracotta dot).
- Right: `caption` "© 2026 MiniMall · 演示项目".
- That's it.

**Page transitions:**
- On every `router.beforeEach`, the outgoing view fades to opacity 0 over 120ms, the incoming view fades from opacity 0 + translateY 8px to opacity 1 + 0 over 220ms.
- Implementation: `<Transition name="page" mode="out-in">` wrapping `<router-view>` inside `DefaultLayout`.

### 5.2 Login

- Two-column. Left 55% **AuthHero**, right 45% form.
- Left:
  - Vertically centered.
  - `display-xl`: "Welcome back."
  - `body` ink-500: "Sign in to continue shopping at MiniMall."
  - Behind the text, a single large `--canvas-darker` blurred circle (300px, blur 60), decorative.
  - Top-left corner: full `BrandMark`.
- Right:
  - 420px max-width form, vertically centered.
  - `h1` "登录".
  - Form: username, password — using `el-form` validation under the hood, wrapped in `<Field>` so the visible input is ours (paper-thin underline-style or 1px ink-100 box, focus expands to ink-700).
  - Primary `<Button variant="primary" size="lg">` 100% wide: "登录".
  - Below: "还没有账号？ <text-link>立即注册</text-link>" caption.
- Mobile: single column, hero collapses to a 120px banner with just the headline.

### 5.3 Register

- Same shell as login.
- Left hero text: "Create your MiniMall account." / "Takes less than a minute."
- Right form: username, password, confirm password.
- Below the form: small ink-500 caption "演示账号，不会发送短信或邮件验证。".

### 5.4 Product list

**Top (editorial)**:
- 1280px container, padding-top 64.
- Left: `display` headline "Shop".
- Below headline: `caption` "{total} items in store".
- Right: `<PillGroup>` filter — "全部" / "在售" / "已下架". Active pill is ink-900 fill, white text. Inactive pills are ghost (transparent + ink-300 border + ink-700 text), hover to ink-100 fill.
- Bottom hairline divider, margin-top 32.

**Grid**:
- xl ≥ 1280: 4 cols, gutter 24, row-gap 48.
- lg ≥ 1024: 3 cols, gutter 24, row-gap 48.
- md ≥ 640: 2 cols, gutter 20, row-gap 32.
- sm: 1 col, full width.

**ProductCard**:
- `<ProductCover>` 4:5 at top, `radius-lg`.
- Below cover, padding-top 16:
  - `h2`-sized name (18/600 ink-900), single line, ellipsis.
  - Row: `<PriceText size="md">` left, `<DotStatus>` right.
- Whole card is clickable; no "View details" button.
- Hover (≥ md): cover scales 1.02 over 220ms, name translates up 2px.
- Off-shelf: cover desaturated, name ink-500, "已下架" pill on cover.

**Loading**:
- `<ProductListSkeleton count="8">` — 8 skeleton cards matching real layout.

**Empty**:
- `<EmptyState title="Shelf is empty for now." sub="Check back later for new arrivals." cta="Refresh" @cta="fetchProducts">` — no cardboard box icon. Optional decorative `canvas-darker` blob behind.

**Error**:
- `<ErrorState title="Couldn't load products." sub="{message}" @retry>`.

**Pagination**:
- `<Pager>` centered, 48 top margin. Text-link "Previous" + page chips (1 2 3 …) + "Next". No EP styling.

### 5.5 Product detail

**Container:** 1200, padding-top 48.

**Top bar:** breadcrumb-style — "Shop ▸ {product.name}", `caption` ink-500, click "Shop" returns to list with preserved query.

**Two-column (≥ md):**

**Left (60%) — `<ProductHero>`:**
- Big `<ProductCover>` 4:5, `radius-xl`, detail-grade rendering (§4.6).
- Below hero: row of 3 variant thumbnails (`<ProductCover variant="1|2|3" size="56">`), 12px gap. Selected thumbnail has a 2px ink-900 outline offset by 4px. Clicking re-seeds the hero geometry with a 220ms cross-fade.
- Below thumbnails (margin-top 32): `h2` "About this product".
- Below: prose description, max-width 540, `body` ink-700, line-height 1.7. `white-space: pre-wrap` preserved.
- Below description (margin-top 32, hairline above): `caption` ink-500 demo notice: "本演示项目不涉及真实物流。模拟下单后可在「我的订单」查看流程。".

**Right (40%) — `<PurchasePanel>` (sticky, top: 96, max-width 400):**
- Product name `h1`, ink-900.
- Price block (margin-top 8): `<PriceText size="xl">` 32/700 ink-900.
- Status row (margin-top 16): `<DotStatus>` "在售" terracotta / "已下架" ink-500 / "已售罄" warn. If in stock and stock ≤ 5: warn-tone caption "仅剩 N 件".
- Hairline (margin-top 24).
- Quantity row (margin-top 24): `body-strong` "数量" left, `<QuantityStepper>` right.
- `<Button variant="primary" size="lg" full>` "立即下单" (margin-top 24). Disabled state: ink-300 background, no shadow, cursor `not-allowed`.
- Disabled hint (margin-top 12): inside a `--accent-terracotta-soft` chip with terracotta text, only when there's a hint.
- Bottom (margin-top 24, top hairline): `caption` mono ink-300 "商品编号 {productId}".

**Mobile (< md):** Stacks. Hero first, then purchase panel becomes a sticky bottom bar (96 high) once the user scrolls past the name — contains price + qty + "立即下单".

**Loading:** `<ProductDetailSkeleton>` — hero rectangle + 3 thumb dots + skeleton text lines + purchase panel skeleton on the right.

**Errors:** `<ErrorState>` replaces the entire content area.

### 5.6 Checkout

**Container:** 560. Padding-top 48.

**Top:** tiny breadcrumb-step "1 商品 · **2 确认** · 3 支付". Step 2 ink-900, others ink-300. `caption` size.

**Title:** `h1` "确认下单" (margin-top 16).

**Card (margin-top 24, white surface, `radius-lg`, no border, shadow-1):**

- `<OrderItemRow>`:
  - Left: `<ProductCover size="80">` 1:1, `radius-md`.
  - Middle: product name `body-strong`, mono `caption` ink-300 product ID below.
  - Right: `caption` ink-500 "单价 ¥X · 数量 ×N", and below it `<PriceText size="md">` for the line subtotal.
  - Padding 16, row gap 16.
- Hairline.
- Stock row: just one inline line, `caption` ink-500 "现货充足，可立即下单" OR `caption` warn "仅剩 N 件，请尽快确认". Not a card, not bordered. If error, replace with `<Notice tone="warn">` inline.
- Hairline.
- Total row: `body-strong` "合计" left, `<PriceText size="lg">` (24/700 ink-900) right.
- Below total, right-aligned: `caption` ink-300 "订单最终金额以后端结算为准".

**Submit error:** `<Notice tone="danger">` between card and actions.

**Actions (margin-top 24):**
- Two-button row, gap 12.
- Right (60%): `<Button variant="primary" size="lg" :loading="submitting">` "确认下单".
- Left (40%): `<Button variant="ghost" size="lg">` "取消".

**Disabled hint:** below actions, `caption` warn, right-aligned, only when there's a hint and not submitting.

**Bad params / 404 / generic error:** full-page `<ErrorState>` replacing everything below the header.

**Loading:** `<CheckoutSkeleton>`.

### 5.7 Orders list

**Container:** 920.

**Top:**
- `display` "My Orders".
- `caption` "{total} orders".
- Below (margin-top 24): `<PillGroup>` "全部 / 待支付 / 已支付 / 已取消". **Client-side filter** over the currently loaded page only. A small ink-300 helper appears if filter yields 0 on current page: "本页无匹配订单 · 可翻页查看其他状态".

**Order list** (margin-top 24, gap 16):

**OrderCard:**
- White surface, `radius-lg`, padding 20, shadow-1, hover lifts (shadow-3 + translateY -1).
- Pending payment cards have a 2px top stripe in `--accent-terracotta` (margin: -20 -20 16 -20).
- Layout: grid `[cover | main | side]` columns `64 1fr auto`.
  - Cover: `<ProductCover size="64">` of the first item.
  - Main column: `<DotStatus>` (top), product summary `body-strong` (e.g. "iPhone 15 Pro × 1 等 2 件"), `caption` mono ink-300 order#, `caption` ink-500 "下单 {datetime}". If pending: `<CountdownChip until="{expireAt}">` (live ticking warm chip).
  - Side column: `<PriceText size="md">` top, action buttons below.
- Actions (pending): `<Button variant="ghost" size="sm">取消</Button>` `<Button variant="primary" size="sm">去支付</Button>`. Other states: single `<Button variant="ghost" size="sm">查看详情</Button>`. All `@click.stop`.
- Whole card clickable to detail.

**Loading:** `<OrdersListSkeleton count="4">`.
**Empty:** `<EmptyState title="No orders yet." sub="Once you've placed an order, it'll show up here." cta="Start shopping" @cta="goShopping">`.
**Error:** `<ErrorState>`.

**Pager:** `<Pager>` centered, margin-top 32.

### 5.8 Order detail

**Container:** 720.

**Top breadcrumb:** "My Orders ▸ {orderNo (last 8 chars)}".

**`<OrderStepper>`** (margin-top 24):
- Three steps: "下单" → "已支付" → "完成".
- Layout: horizontal flex, each step has a 16px circle + label below.
- Finished step: ink-900 circle, ink-700 label.
- Current step: terracotta filled circle, ink-900 label, gentle 1.2s breathing pulse.
- Unfinished step: ink-300 ring (no fill), ink-300 label.
- Lines between steps: ink-300 (unfinished) or ink-900 (finished).
- Cancelled state: replaces the entire stepper with a single ink-500 pill "已取消 · {cancelledAt}".

**Order item rows** (margin-top 32): list of `<OrderItemRow>`, but slightly larger covers (72px) and gap 12.

**Summary block** (margin-top 32, top hairline):
- `body-strong` "合计" left, `<PriceText size="lg">` right.

**Meta block** (margin-top 24, top hairline):
- Three columns, `caption` ink-500 label / `body` ink-700 value:
  - "订单号" / mono `{orderNo}`
  - "下单时间" / `{createdAt}`
  - "支付截止" / `{expireAt}` (only if pending)

**Actions** (margin-top 32, right-aligned):
- Pending: `<Button variant="ghost">取消</Button>` `<Button variant="primary">去支付</Button>`.
- Paid: `<Button variant="primary">查看支付凭证</Button>`.
- Cancelled / closed: `<Button variant="ghost">返回订单列表</Button>`.

**Loading:** `<OrderDetailSkeleton>`.
**Errors:** `<ErrorState>`.

### 5.9 Payment

**Container:** 480. Focused, narrow.

**Top:**
- `<OrderStepper :status>` repeated (continuity with order detail).
- Below stepper: `<Notice tone="neutral" hairline>` thin chip "Demo payment · 模拟链路，不会发生真实扣款".

**`<AmountDisplay>` (margin-top 40, centered):**
- Tiny `caption` ink-500 uppercase letter-spaced "应付金额".
- `amount-display` 56/700 ink-900, tabular-nums, centered.
- On mount, the number tweens from 0 to the final value over 420ms (`--dur-3`).
- Decorative: a 24px terracotta dot above the label (margin-bottom 12).

**Payment method (margin-top 32):**
- A single 100%-wide method card, ink-900 outline 2px (selected), `radius-md`, padding 16.
- Content: small "Mock" badge (mono ink-300 caption) + "模拟支付" body-strong + ink-500 caption "演示渠道 · 立即标记订单为已支付". Right: a small terracotta-stroked check icon.

**Submit error (margin-top 16):** `<Notice tone="danger">`.

**CTA (margin-top 24):** `<Button variant="primary" size="lg" full :loading="submitting">` "确认支付 ¥123.45". Loading state: button content swaps to "处理中…" with three pulsing dots (1.2s stagger).

**Polling hint (during submit):** below CTA, `caption` ink-500 centered "正在等待订单状态更新…".

**Success state** — replaces the AmountDisplay + method + CTA block (stepper stays):

- Centered:
  - 96×96 circle, `--accent-terracotta-soft` background, with a terracotta check icon (28×28). The check renders with a `stroke-dashoffset` animation drawing in over 600ms on mount.
  - `display` "支付成功" (margin-top 16).
  - `caption` ink-500 "模拟支付链路 · 订单已标记为已支付" (margin-top 4).
- Receipt block (margin-top 32, top hairline):
  - 4 rows: 支付单号 (mono) / 渠道 / 金额 / 时间.
- Two CTAs side by side (margin-top 32, full width split):
  - `<Button variant="ghost">继续逛逛</Button>` (50%)
  - `<Button variant="primary">查看订单详情</Button>` (50%)

**success-pending state:**
- Same skeleton, but the 96 circle is `--canvas-darker` with a gentle hourglass icon.
- Title `display` "支付已提交".
- Sub: "支付请求已被受理，订单状态稍后更新。".
- Single CTA: `<Button variant="primary">再次刷新订单</Button>` + small ghost "返回订单详情".

**already-paid state:**
- Same skeleton, success circle (no animation, since not fresh).
- Title `display` "该订单已完成支付".
- Receipt rows as success.
- CTA: `<Button variant="primary">查看订单详情</Button>`.

**not-payable state:**
- Neutral 96 circle (canvas-darker) with an info icon.
- Title `display` "无法发起支付".
- Sub: "当前订单状态为 {label}，无法发起支付。".
- CTAs: "返回订单列表" ghost + "查看订单详情" primary.

**Loading:** `<PaymentSkeleton>`.

### 5.10 403 / 404

- Full canvas. No card.
- Vertically centered.
- `display-xl` in ink-300: "404" / "403".
- `h1` ink-900 below: "Page not found." / "You don't have access to this."
- `body` ink-500: explanatory sentence.
- `<Button variant="primary" size="lg">` "回到商品列表".
- Tiny `caption` ink-300 at bottom with a quiet `BrandMark`.

---

## 6. Loading, empty, and error states (cross-cutting)

### 6.1 Loading

**Forbidden:** `v-loading` directive, EP spinner, `el-skeleton`.

**Required:** custom `<Skeleton>` atom + per-page composite skeleton, structurally matching the real layout. Shimmer animation: linear-gradient sweeping `-100% → 100%` over 1.6s ease-in-out, infinite, on `--canvas-darker` base.

Skeletons render immediately on `loading=true`, not after a delay. They replace the real content, not overlay it.

### 6.2 Empty

`<EmptyState title sub cta @cta>` — a tall vertical block with optional `--canvas-darker` blurred circle decoration. Voice: short, human, slightly warm.

| Page | Title | Sub |
|---|---|---|
| Product list | Shelf is empty for now. | Check back later for new arrivals. |
| Orders list | No orders yet. | Once you've placed an order, it'll show up here. |

### 6.3 Error

`<ErrorState title sub @retry secondary?>` — same structural footprint as EmptyState. Mood: calm, not alarmed. Single `<Button variant="primary">重试</Button>` + optional ghost secondary.

---

## 7. Motion vocabulary

| Moment | Spec |
|---|---|
| Page in | `<Transition>` opacity 0→1 + translateY 8→0, 220ms `--ease` |
| Card hover | translateY -2px + shadow-1 → shadow-3, 220ms |
| Button press | scale(0.98), 120ms ease-out |
| Button focus | shadow-press ring, 120ms |
| Skeleton shimmer | sweep gradient, 1.6s linear infinite |
| Number tween (amount on payment mount) | 0 → final, 420ms, ease-out cubic |
| Quantity stepper change | flash ink-300 → ink-900 on the digit, 180ms |
| Pending dot | breathing scale 1 ↔ 1.1, 1.2s ease-in-out infinite |
| Success check draw | stroke-dashoffset 100 → 0, 600ms ease-out, after the circle fades in |
| Variant thumb swap | cover cross-fade 220ms |
| Header collapse | height 72 → 56 + border-bottom + backdrop-blur, 200ms |

Motion is calm, never bouncy. No `cubic-bezier(.68,-.55,...)` springs. No 600ms+ entrances except success ceremony.

---

## 8. Implementation plan

**Strategy:** Foundation first (tokens + AppShell + atoms), then page-by-page vertical slices. Each commit produces a working state, builds clean, smoke-tested. Use the existing `dev-log.md` per-task format. Each step requires a fresh "go" before committing (per memory rule [[feedback-commit-authorization]]).

### Phase A — Foundation

**A1. Tokens & global base**
- `src/styles/tokens.css` (CSS custom properties for color/space/radius/shadow/duration/easing).
- `src/styles/base.css` (resets, body font, canvas background, focus ring, `::selection`, scrollbar).
- `src/styles/typography.css` (text utility classes for the type scale).
- Update `main.ts` to import these **after** `element-plus/dist/index.css` so our `:root` variables and global base styles win the cascade. (Earlier draft said "before"; that was a cascade-direction error.)
- Load Inter + Inter Tight via Google Fonts in `index.html` (`<link rel="preconnect">` + `<link rel="stylesheet">`); falls back to PingFang SC / system-ui via the `--font-sans` / `--font-display` stacks if the CDN is unreachable.
- **A1 does NOT override Element Plus variables.** The "minimal EP override for el-message / el-message-box / el-form-item" originally placed in A1 is deferred to the end of Phase B, once we know exactly which EP surfaces remain. A1 must be a zero-visual-regression step.
- No visual change to pages yet.

**A2. AppShell**
- New `AppHeader.vue` (scroll-aware), `AppFooter.vue`, `BrandMark.vue`.
- Update `DefaultLayout.vue`: header + page transition wrapper + footer; canvas color body.
- All pages immediately use the new shell.
- `npm run build` + visually confirm header & footer.

**A3. Atom components**
- `Button.vue`, `Pill.vue`, `DotStatus.vue`, `PriceText.vue`, `Hairline.vue`, `Field.vue`, `QuantityStepper.vue`, `Skeleton.vue`, `SkeletonText.vue`, `Notice.vue`, `Pager.vue`, `PillGroup.vue`.
- Not yet wired into pages. A `src/views/__designprobe__/` route is OK if needed for spot-checking — must NOT be linked from production nav; will be removed in Phase D.

**A4. ProductCover system**
- `ProductCover.vue` with hash, palette set, geometry set, initial mark.
- `src/utils/cover.ts` — pure hash + palette/geometry selection (unit-testable).
- Vitest spec for determinism: same productId → same cover output.
- Add a one-off design probe page (temporary) showing 24 covers with sample ids to eyeball the palette range.

**A5. EmptyState / ErrorState**
- `EmptyState.vue`, `ErrorState.vue`.
- Replace all `el-empty` / `el-result` usages page-by-page in Phase B.

**Verification after Phase A:** `npm run build` clean, type-check clean, all existing pages still functional with the new shell, no visual regression on inner pages (they still look like before because pages haven't been touched yet — this is expected and OK).

### Phase B — Page vertical slices

Each step is one commit, gated by user "go" per [[feedback-commit-authorization]].

| Step | Page | New molecules introduced |
|---|---|---|
| B1 | Product list | `ProductCard` (new), `ProductCardSkeleton`, `ProductListSkeleton` |
| B2 | Product detail | `ProductHero`, `PurchasePanel`, `ProductDetailSkeleton` |
| B3 | Checkout | `OrderItemRow`, `OrderSummary`, `CheckoutSkeleton` |
| B4 | Orders list | `OrderCard`, `CountdownChip`, `OrdersListSkeleton`, client-side tab filter |
| B5 | Order detail | `OrderStepper`, `OrderDetailSkeleton` |
| B6 | Payment | `AmountDisplay`, `PaymentResultPanel`, `PaymentSkeleton` |
| B7 | Login + Register | `AuthHero`, `AuthFormCard` |
| B8 | 403 + 404 | `BigErrorPage` |

Per-step workflow (per [[feedback-phase1-frontend-workflow]]):
1. Print branch, current task target, split recommendation, implementation plan, expected files, verification commands.
2. Wait for user confirmation.
3. Edit view + add the needed components.
4. `npm run build`, `npx vitest run` (where applicable).
5. Manually smoke through the affected user flow.
6. Append dev-log entry (Date / Status / Branch / Implemented / Changed files / Commands / Result / Issues / Next).
7. Print wrap-up + proposed commit message.
8. Wait for user confirmation.
9. Commit only this step's files (including dev-log).

### Phase C — Motion polish & cleanup

**C1.** Page transitions wired in `DefaultLayout`.
**C2.** Skeleton shimmer timing audit across all pages.
**C3.** Number animation on `AmountDisplay`.
**C4.** Success check stroke-dashoffset animation on payment success.
**C5.** Remove the temporary design-probe route if it exists.
**C6.** Final dev-log summary entry: "Phase 1 UI rework — complete".

### Phase D — Verification gate

**D1.** Full smoke (see §9.4) — every step.
**D2.** Visual audit (see §10) — no forbidden artifacts remain.
**D3.** `npm run build` final.
**D4.** Branch summary in dev-log.

---

## 9. Risk register

### 9.1 What could break the purchase loop

| Risk | Mitigation |
|---|---|
| `QuantityStepper` v-model contract differs from `el-input-number` | New stepper exposes the identical `:min :max :disabled :step` props and emits `update:modelValue` with an integer. Internal impl uses a native `<input type="number">` for accessibility, but the visible chrome is custom. Vitest covers clamp behavior. |
| Removing `v-loading` accidentally hides errors mid-load | Skeletons render only when `loading=true`. When `loading=false` and content empty, `<EmptyState>` shows. When error, `<ErrorState>` shows. Mutually exclusive branches preserved from the existing code. |
| Sticky `PurchasePanel` overlaps collapsed header | `top: 96px` accounts for collapsed header (56) + 40 breathing. Verified on the smoke run. |
| Client-side tab filter on orders list hides orders the user expects to see | Add an inline `caption` "本页无匹配订单 · 可翻页查看其他状态" when filter yields 0 on the current page but `totalElements > 0`. Don't oversell the filter as "filter all my orders". |
| Variant thumbnail click re-renders hero in a way that confuses users into thinking it's a different SKU | Add ARIA label "Visual style — purely decorative". No re-fetch, no inventory change, no analytics event. Pure CSS swap. |
| Page transition breaks router navigation guards | `<Transition mode="out-in">` runs *after* `router.beforeEach` resolves; guards are unaffected. Confirmed in Vue Router docs. |
| Skeleton shimmer triggers layout reflow | Skeletons use `transform` and `opacity` on a gradient overlay, not width/height changes. No reflow. |

### 9.2 What is style-only (no logic change)

- All atoms & molecules in §3.
- Skeleton replacement of `v-loading`.
- ProductCover renderer.
- Color, type, spacing.
- Motion.

### 9.3 What touches logic (and only in `<script setup>`'s computed/render layer)

- **Orders list tab filter** — adds a `statusFilter` ref + `filteredOrders` computed. Does NOT touch the API call or pagination behavior.
- **Variant thumbnail click on product detail** — adds a `coverVariant` ref. Does NOT touch product data.
- **`AmountDisplay` mount tween** — local ref that animates `displayedAmount` toward `order.totalAmount` on mount. Does NOT touch payment logic.
- **Header scroll-aware collapse** — adds a window scroll listener in `AppHeader.vue` to toggle a `scrolled` class. Cleanup on unmount.

Everything in `src/api/`, `src/stores/`, `src/router/`, `src/utils/order-status.ts`, and the `<script setup>` state machines of CheckoutView / PaymentView / OrdersView / OrderDetailView (loading state, error branching, idempotency, polling, run-id tracking) — **stays untouched**.

### 9.4 Smoke flow (run after every Phase B step that affects it)

1. Register → redirect to `/login`.
2. Login → land on `/products` (transition fades).
3. Product list: filter pill switches between 全部 / 在售 / 已下架, page through, URL syncs.
4. Enter an in-stock product detail → Hero + 3 thumb + sticky panel.
5. Adjust quantity to max, then max+1 (blocked), then 0 (blocked).
6. Click 立即下单 → Checkout 560 container, item row with cover, total correct.
7. Submit → redirect to Order Detail, stepper at step 1.
8. From Order Detail, click 去支付 → Payment 480 container, AmountDisplay tweens.
9. Click 确认支付 → loading dots → polling → success ceremony (check stroke, receipt slides in).
10. Click 查看订单详情 → stepper now at step 2 (paid).
11. Back to My Orders → tab filter "已支付" shows the new order.
12. Cancel a different pending order → toast + refresh.
13. Hit `/orders/__nope__` → BigErrorPage 404.
14. Manually clear token → next API call 401 → redirect to /login.
15. Visual audit: open every page and confirm:
    - No EP blue button visible.
    - No `el-result` cloud/warning icons.
    - No `el-empty` cardboard.
    - No `el-tag` strong fills.
    - No `v-loading` gray spinner.
    - No red price.
    - Header collapses on scroll.
    - Footer present.

---

## 10. Behavioral rules during implementation (binding)

This section is the bar I commit to maintaining. If any of these slip, the step is wrong.

**R1.** No EP component visible to the user on storefront surfaces, except: `ElMessage` toast, `ElMessageBox.confirm`, `el-form` / `el-form-item` validation messages, `<el-input>` *wrapped* inside `<Field>`. Everything else is replaced.

**R2.** No `v-loading` anywhere customer-facing. Skeletons or nothing.

**R3.** No `type="primary"` blue button. Black-fill via `<Button variant="primary">`.

**R4.** No red price. Price is `--ink-900`.

**R5.** No "label : value" form-style rows for product/order/payment info on user-facing pages. Use editorial layout — name, prose, summary, hairline.

**R6.** Same product ID renders the same cover everywhere. Drift = bug.

**R7.** Container widths follow §5 per page. Don't default everything to 1200.

**R8.** Motion specs in §7 are not "polish later" — they ship with the page that uses them.

**R9.** `src/api/`, `src/stores/`, `src/router/`, and the state machines of existing views — untouched. Only `<template>` and `<style>` rewrites + the additive computed/refs listed in §9.3.

**R10.** Every commit is a working state. `npm run build` green. Per-task dev-log entry. Per-task user "go" before commit.

**R11.** Don't fabricate features for visual filler. Variant thumbnails are explicitly decorative (and labeled as such for a11y). No fake reviews, no fake "trending", no fake categories, no fake "you may also like".

**R12.** If a design decision turns out to clash with the smoke flow, **stop and fix the design**, don't bend the flow.

---

## 11. PRD boundary self-check

- ✅ User-facing storefront only.
- ✅ No new business modules. (Tab filter on orders is a client-side derive over already-fetched data.)
- ✅ No admin console.
- ✅ No RBAC.
- ✅ No cart / address / coupon / real payment / refund / aftersales.
- ✅ No backend business logic changes.
- ✅ No API contract changes.
- ✅ All API calls go through api-gateway canonical `/api/**`.
- ✅ No `/internal/**` calls.
- ✅ No direct microservice port access.
- ✅ Existing purchase loop preserved: register → login → list → detail → inventory → order → detail → mock pay → PAID.
- ✅ No new product fields (cover is client-side computed).

---

## 12. Glossary

- **Storefront surface** — any page or component the customer sees: list, detail, checkout, orders, order detail, payment, login, register, errors. NOT: form validation tooltips, toasts, confirm dialogs.
- **Detail-grade** (cover) — the higher-fidelity rendering reserved for the product detail hero (§4.6).
- **Variant thumbnail** — purely decorative cover thumbnails on the detail page (§4.7).
- **Vertical slice** — implementing one full page (template + style + needed molecules + skeleton + empty/error) in a single commit.
- **Ceremony** — the brief intentional moment on payment success (§5.9): check stroke animation + receipt reveal + dual CTA.
