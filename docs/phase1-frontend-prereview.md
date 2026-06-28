# Phase 1 前端接手前置审查（只读）

- 日期：2026-05-21
- 审查范围：只读通读后端代码库 + Phase 1 文档/PRD/TaskMaster 任务树，核对契约真伪，记录风险点与未对齐点。
- 审查方式：直接阅读后端源码（controller / DTO / enum / 网关过滤器 / query service），不依赖文档表述。
- 结论：未修改任何代码，未创建 frontend 目录，未执行 npm install。

---

## 一、总体评价（真实）

后端契约整体**干净、自洽、边界清晰**，适合前端直接对接：

- 所有公开 REST 均通过 `ApiResponse<T>{success, code, message, data}` 统一封装（`common-core/.../response/ApiResponse.java`）。
- 分页统一为 `PageResponse<T>{content, page, size, totalElements, totalPages}`，0-based。
- 网关与下游服务**共用同一套 `ErrorCode` 枚举**，错误响应格式一致，不会出现两套错误体。
- 安全边界已落地：网关剥离浏览器传入的 `X-User-Id/X-Username` 并从 JWT 注入可信值；`/internal/**` 在网关被显式拒绝。
- 状态字段全部使用枚举，业务异常走 `BusinessException`，符合后端硬规则。

**任务树合理**：`validate-dependencies` 通过（10 任务、26 依赖、0 无效），拓扑正确（脚手架 → API client → 路由/认证 → 各页面 → 验收），task 2（API client）为关键瓶颈，符合工程直觉。

**主要风险集中在"文档表述与代码实现的细节偏差"**，而非后端缺陷。其中 1 个为高优先（订单数据结构），其余为中/低。下面逐条列出，**全部已用源码核实**。

---

## 二、已核实的真实契约（以代码为准）

> 这些是前端 `src/types/*` 和 API client 必须照抄的事实，优先级高于 `docs/frontend-integration.md` 的示例。

### 用户 `/api/users/**`
- `POST /register` 入参校验（`RegisterRequest`）：`username` 非空 ≤64；`password` **最少 6 位** ≤128；`email` 可空，但**非空时必须是合法邮箱** ≤128；`phone` 可空 ≤32。
- `POST /register` 返回（`UserResponse`）：`userId, username, email, phone, status`（`status` ∈ `ACTIVE|DISABLED`）。
- `POST /login` 返回（`LoginResponse`）：`token, tokenType("Bearer"), userId, username` —— **登录响应已含 userId/username，可直接初始化 currentUser，无需强制再调 /me**。
- `GET /me` 返回（`CurrentUserResponse`）：**仅 `userId, username`**（无 email/phone）。

### 商品 `/api/products/**`
- `GET /api/products`：参数仅 `status`（枚举 `ON_SHELF|OFF_SHELF`，可空）+ Spring `Pageable`（`page,size,sort`）。**没有 keyword/搜索参数**。`size` 不传时 Spring 默认 20。
- `GET /api/products/{productId}` 返回（`ProductResponse`）：`productId, name, description, price(BigDecimal), status, createdAt, updatedAt`。

### 库存 `/api/inventories/**`
- `GET /{productId}` 返回（`InventoryResponse`）：`productId, availableStock(int), lockedStock(int), stockState`（`IN_STOCK|OUT_OF_STOCK|INACTIVE`）。

### 订单 `/api/orders/**`
- `POST /api/orders` 入参（`CreateOrderRequest`）：`productId` 非空 ≤64；`quantity` 非空 ≥1；`idempotencyKey` **非空** ≤128（下单幂等键后端强制要求）。
- `POST /api/orders` 返回（`CreateOrderResponse`，**扁平**）：`orderNo, userId, status, expireAt, totalAmount, productId, quantity`。
- `GET /api/orders/my` 返回 `PageResponse<OrderSummaryResponse>`。
- `GET /api/orders/{orderNo}` 返回（`OrderDetailResponse`）。
- `OrderSummaryResponse` 与 `OrderDetailResponse` 结构相同：`orderNo, userId, status, totalAmount, items[], createdAt, updatedAt, expireAt, paidAt, closedAt`，且带 `@JsonInclude(NON_NULL)`（**null 字段会被省略**，如未支付时无 `paidAt`）。
  - `items[]` 元素（`OrderItemSummary`）：`productId, productName, quantity, unitPrice`。
  - **Phase 1 订单恒为单商品**：后端 `items()` 写死 `List.of(单个item)`，所以 `items` 长度始终为 1，可直接取 `items[0]`。`productName` 已包含，**列表/详情无需额外再查商品**。
- `POST /api/orders/{orderNo}/cancel` 返回（`CancelOrderResponse`）：`orderNo, userId, productId, status`。
- `OrderStatus` ∈ `PENDING_PAYMENT | PAID | CANCELLED | CLOSED | REFUNDED`。

### 支付 `/api/payments/**`
- `POST /api/payments/{orderNo}/pay`：请求体 `@RequestBody(required=false)`，`PayPaymentRequest{channel, idempotencyKey}` —— **channel 可空（默认 MOCK），idempotencyKey 在支付侧并非强制（无 @NotBlank）**。即便如此，PRD/契约仍要求前端每次支付传唯一 key，应照做。
- `GET /api/payments/{orderNo}` 返回（`PaymentResponse`）：`paymentNo, orderNo, userId, productId, status, amount, channel, paidAt`。
- `PaymentStatus` ∈ `PENDING | SUCCESS | FAILED`；`PaymentChannel` 目前仅 `MOCK`。

### 网关 / 错误码 / 安全（已核实）
- 错误码为**数字字符串**：`SUCCESS=0, BAD_REQUEST=40000, VALIDATION_ERROR=40001, UNAUTHORIZED=40100, FORBIDDEN=40300, NOT_FOUND=40400, CONFLICT=40900, ORDER_CANCELLED=40901, ORDER_INVALID_STATE=40902, PAYMENT_ALREADY_SUCCESS=40903, TOO_MANY_REQUESTS=42900, INTERNAL_ERROR=50000`。
- 网关 401/403/429 通过 `GatewayErrorResponseWriter` 使用**同一 ErrorCode 枚举**输出，即线上 wire `code` 是 `"40100"/"40300"/"42900"`，**不是** `"UNAUTHORIZED"` 这类名字（文档散文里用的是枚举名）。
- 网关端口 `8080`（`API_GATEWAY_PORT`）。CORS 允许 `http://localhost:*` 与 `http://127.0.0.1:*`、方法含 OPTIONS、**`allow_credentials=false`**。限流：令牌桶 replenish 10/s、burst 20、fail-open。

---

## 三、风险点（按优先级）

### R1（高）订单数据结构：`items[]` vs 文档的扁平字段
`docs/frontend-integration.md` 的订单示例展示的是扁平 `productId/quantity`，但实际 `GET /orders/my` 与 `GET /orders/{orderNo}` 返回的是 `items[]` 数组（含 `productName/unitPrice`）。**只有创建订单的响应是扁平的**。
- 影响：若前端类型按文档建模成扁平字段，订单列表/详情会取不到商品信息。
- 建议：`OrderSummary`/`OrderDetail` 类型必须建 `items: OrderItemSummary[]`，页面读 `items[0]`；下单成功响应单独建扁平类型。

### R2（中）错误码 wire 值是数字，不是枚举名
契约散文写 `UNAUTHORIZED/TOO_MANY_REQUESTS`，但 JSON `code` 实际是 `"40100"/"42900"`。
- 建议：API client 的拦截器**优先按 HTTP 状态码**（401/403/429/404/409/500）分流，`ApiResponse.code` 作为细分（如 40901/40902/40903 支付状态刷新）。不要去匹配字符串枚举名。

### R3（中）商品列表无搜索；列表默认不过滤状态
后端 `GET /api/products` 无 `keyword`，且 `status` 不传时返回全部状态商品（含 OFF_SHELF）。
- 影响：PRD 6.4 把 keyword 标为"以后端为准"——结论是**不做后端搜索**（如需搜索只能前端本地过滤当前页，价值有限，建议 Phase 1 不做）。下架商品是否出现在列表由前端决定是否带 `status=ON_SHELF`。
- 建议：列表默认 `status=ON_SHELF`；若要展示下架商品需弱化入口、详情禁用下单（与 PRD 6.4/6.5 一致）。

### R4（中）未支付订单查支付详情会返回 404
`PaymentQueryService`：无支付记录 → `NOT_FOUND(40400)`。PENDING 订单从未支付时 `GET /api/payments/{orderNo}` 会 404。
- 建议：订单详情页/支付页拉取支付信息时，把 404 当作"暂无支付信息"的空状态处理，**不要弹全局错误**，更不能因此判定订单异常。

### R5（中）跨用户/不存在订单都返回 404，而非 403
`OrderQueryService.detail` 用 `findByOrderNoAndUserId`：访问别人的订单与订单不存在**都返回 `NOT_FOUND(40400)`**，不会返回 403。
- 影响：PRD 6.8/13.3 设想的"无权访问 → /403"在订单场景**不会触发**；`/403` 页面实际只会被网关 FORBIDDEN(40300) 触发，而前端正常不会碰到（如调用 /internal）。
- 建议：订单详情把 404 统一渲染为"订单不存在或无权访问"友好提示即可；`/403` 路由仍保留但视为兜底，不要围绕它设计订单鉴权 UX。

### R6（低）`/me` 字段精简
`/me` 只有 `userId/username`，无 email/phone；登录响应也无 email/phone（只有注册响应有）。
- 建议：authStore.currentUser 仅建模 `userId/username`，不要在导航栏/资料处期望 email/phone。

### R7（低）CORS 不带凭证
`allow_credentials=false` → 前端**不能用 cookie**，必须用 `Authorization: Bearer`，axios 保持 `withCredentials=false`。token 存 localStorage（PRD 第八节）即可。

### R8（低）金额为 BigDecimal
`price/totalAmount/unitPrice/amount` 是 BigDecimal，JSON 序列化为数字。
- 建议：展示用固定两位（如 `toFixed(2)` 或 Intl 货币格式），避免浮点显示问题；不要参与不必要的前端数值运算。

### R9（低）订单超时会变 CLOSED
订单有 `expireAt` 且后端有超时关单逻辑，用户停留期间 PENDING_PAYMENT 可能变为 CLOSED。
- 建议：支付页/详情页操作前以最新拉取的 status 为准，操作失败（如已关单/已取消）时刷新并展示后端 message（对应 40901/40902）。

### R10（低）`/api/products/**` 写接口与 on-shelf/off-shelf 存在但禁用
`ProductController` 含 create/update/on-shelf/off-shelf，但属后端能力、非 admin-safe。
- 建议：Phase 1 前端**绝不调用**这些端点（无购物车/管理台/上下架 UI），只用 `GET`。

---

## 四、已对齐决策（2026-05-21 确认）

> 以下为与负责人对齐后的最终口径，后续任务以此为准。

1. **环境变量名**：统一用 `VITE_API_BASE_URL`，默认 `http://localhost:8080`；该名称也已同步到 `docs/frontend-integration.md`。
2. **商品列表搜索**：**不做**。后端无 keyword，Phase 1 不实现搜索框（含本地过滤），避免无效请求。
3. **商品列表状态展示**：**全部展示并弱化下架**。列表拉取全部状态（不强制 `status=ON_SHELF`）；`OFF_SHELF` 商品打灰色"已下架"标签、弱化购买入口；详情页对下架商品禁用下单。（对应 R3，按 PRD 6.4 描述实现。）
4. **后端文档修订**：`docs/frontend-integration.md` 的订单示例（扁平 → `items[]`）与错误码 wire 值说明，**放进 Task #12（文档与验收）一并更正**，本阶段不单独改。
5. **`/403` 页面定位**：**保留作兜底**。订单越权返回 404，统一渲染为"订单不存在或无权访问"；`/403` 仅兜底网关 FORBIDDEN(40300)，不围绕它设计鉴权 UX。
6. **登录后初始化用户**：**登录直接用响应中的 userId/username 初始化 currentUser**（省一次请求）；页面刷新/恢复 token 时再调 `GET /api/users/me` 校验有效性，失效则清 token 跳登录。

---

## 五、起步建议

- **Task #1（初始化前端工程）无任何契约依赖**，可安全先做（脚手架 + .env.example 指向网关 + README）。
- 进入 **Task #2（API client + 类型）前**，建议先敲定上面第 1/2/4 点（变量名、搜索、文档修订口径），因为它们直接决定 `src/types/*` 与拦截器实现。
- 后续 **Task #8/#9（订单/支付页）** 强依赖 R1/R4/R5 的处理方式，实现时以本文件"已核实契约"为准。
