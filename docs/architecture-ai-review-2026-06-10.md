# MiniMall 全仓架构体检 + AI 库存助手中期审查报告

- 审查日期: 2026-06-10
- 分支: `codex/phase3-ai-inventory-assistant`
- 方式: 只读审查（未修改任何代码、配置、数据库脚本）
- 审查范围: 全部后端模块、双前端、网关、数据库迁移、TaskMaster 任务树、dev-log、Phase 3 契约与 PRD

---

## 第一部分：事实盘点（不含结论）

### 项目结构

- 后端 10 个 Maven 模块: `common-core`、`common-auth`、`api-gateway`、`user/product/inventory/order/payment/notification-service`
- 两个独立前端: `frontend`（顾客端，10 视图）、`admin-frontend`（管理端，7 路由 + 测试）
- 基础设施: `docker-compose.yml`（MySQL/Redis/RabbitMQ/Prometheus/Grafana + 7 个 Java 容器，服务端口不发布、仅网关可达）、`docs/sql/migrations/` V1–V12 手工迁移
- 过程资产: TaskMaster 6 个 tag（前 5 个全部 done，phase3 为 6/13）、`docs/dev-log.md` 每任务 9 字段记录、每阶段 PRD + 契约 + 验收文档

### AI 助手相关资产清单

后端代码（全部在 inventory-service，main 约 3071 行 + 33 个相关测试类）:

| 层 | 文件 |
|---|---|
| Provider 适配 | `ai/AiProvider.java`、`OpenAiCompatibleAiProvider`、`DeepSeekAiProvider`、`MiniMaxAiProvider`、`MockAiProvider`、`AiProviderRestClientFactory` + 7 个契约类型 |
| Prompt | `ai/prompt/AiPromptTemplateCatalog` + `resources/ai/prompts/*.json` 4 个版本化模板（qa/low-stock/hot-products/replenishment 各 v1） |
| 输出校验 | `ai/validation/AiModelOutputValidator`（342 行）+ 5 个支撑类 |
| 分析编排 | `ai/analysis/AiInventoryAnalysisService` |
| 证据门面 | `service/AiInventoryEvidenceFacade`、`client/order/OrderServiceSalesEvidenceClient` |
| API 服务 | `service/AiInventoryQuestionService`、`AiInventoryAnalysisApiService`、`AiOperationSuggestionService`（Phase 2.5） |
| 控制器 | `web/AdminAiInventoryQuestionController`、`AdminAiInventoryAnalysisController`、`AdminAiEvidenceController`、`AdminAiSuggestionController`、`AdminInboundOrderController` |
| 配置 | `config/AiProviderProperties`（`minimall.ai.*` ← `AI_*` 环境变量，默认 MOCK） |

接口清单（已实现）:

- `POST /api/admin/ai/inventory/ask`
- `POST /api/admin/ai/inventory/low-stock-analysis`
- `GET /api/admin/ai/inventory/evidence/current/{productId}` / `low-stock-candidates` / `low-stock-analysis` / `hot-products`
- Phase 2.5: `GET/POST /api/admin/ai-suggestions/**`（list/detail/reject/convert-inbound-draft）、`/api/admin/inbound-orders/**`（draft/list/detail/cancel/confirm）
- 网关 `/api/admin/ai/**` 已路由至 inventory-service（`api-gateway/src/main/resources/application.yml:23`）

数据表:

- `ai_operation_suggestion`（V11 + V12 加 9 个模型元数据列: model_provider/model_name/prompt_version/output_schema_version/validation_status/validation_error/三个 JSON 快照列）
- `ai_operation_suggestion_item`（带 CHECK 约束）
- `inbound_order` / `inbound_order_item`（V8/V9/V10）

前端: **零**。`admin-frontend/src/router/index.ts` 只有 products/inventories/orders/payments/notifications/audit-logs 六个业务路由——没有 `/ai-inventory`，也没有入库单和 AI 建议审批页面。

文档: `phase3-ai-inventory-contract.md`（359 行锁定契约）、`phase3-ai-inventory-assistant-prd.txt`（843 行）、`phase2-5-ai-inventory-contract.md`、phase2.5 验收报告、风险登记册。

TaskMaster 进度: Task 1–6 done，7.1 done（7.2 hot-product 端点、7.3 安全回归 pending），**Task 8（生成建议落库）、9（APPLIED 同步）、10（前端页面）、11–13 全部 pending**。

---

## 第二部分：微服务架构审查

### 逐服务评价

| 服务 | 职责 | 评价 |
|---|---|---|
| api-gateway | JWT 校验、伪造头剥离、ADMIN 路径拦截、内部路径封锁、限流、CORS | **架构最强的一环**。`GatewayAuthenticationFilter` 先剥离 4 个信任头再注入（:90-105），网关层就拒绝 USER 访问 `/api/admin/**`（:67） |
| user-service | 注册/登录/admin 登录/审计日志查询 | 合理。BCrypt、`adminLogin` 单独校验 `UserRole.ADMIN`、管理员种子由环境变量控制 |
| product-service | 商品 CRUD、上下架、`/internal/products` 快照 | 合理，user/admin/internal 三层控制器分离清晰 |
| inventory-service | 库存 + 入库单 + AI 建议 + **整个 AI 助手** | 职责最重。AI 放这里符合 PRD §7 的论证（避免新增分布式事务边界），当前规模下可接受，不建议拆 |
| order-service | 订单 + 状态机 + 超时取消 + 销量统计 | 合理。依赖方向正确: order→product/inventory 单向 HTTP |
| payment-service | 支付 + 事件发布 | **有边界违规**，见下 |
| notification-service | 消费事件 + 通知日志 | 合理且最小 |
| common-core/auth | 响应/异常/审计契约/JWT/信任过滤器 | 未膨胀，全是真实共享物 |

### 三个真实的架构问题

1. **payment-service 直接映射 order-service 的表**。`payment-service/src/main/java/com/minimall/payment/domain/Order.java` + `repository/OrderRepository.java` 直接读 `orders` 表做支付前校验。同一系统里 order→inventory 走 HTTP，payment→orders 却走共享表，模式不一致，是面试最容易被打的点。（全部服务共用 `minimall_order` 单库本身是已知取舍，但"跨服务读他人表"超出了"共享实例、独立表集"的辩护范围。）

2. **lockedStock 永不消耗**。下单 deduct 是 `available -= q, locked += q`；取消 release 反向；但支付成功后（`order-service/messaging/PaymentSuccessEventConsumer`）只改订单状态，没有任何消耗 locked 的步骤。已支付订单的锁定库存永久滞留，`locked` 单调增长。AI 分析用的 `availableStock` 语义没错，但"锁定库存"对 AI 证据是噪音项。这是建模简化，必须能讲清楚。

3. **订单创建 saga 缺口**。`OrderCommandService.createNewOrder`（:118-138）先远程扣库存、后本地 insert 订单。若 insert 失败，已锁库存无主——超时取消器只扫订单表，扫不到不存在的订单，锁定库存泄漏。概率低但真实存在。

### 与 AI 助手的耦合

AI 代码对库存写路径**零耦合**——AI 包内没有任何 repository 写调用，唯一写路径 `AiOperationSuggestionService`/`InboundOrderDraftService` 都要求 `InventoryAdminAuditContext`（即真实管理员身份）。

`OrderServiceSalesEvidenceClient` 直连 order-service 端口的 `/api/admin/operation-stats/sales-by-product`（带 `X-Internal-Token` + UserContext 透传）——这是服务间调用，没违反"浏览器只走网关"，但复用 admin 路径而非 `/internal/**` 与项目自身惯例不一致，需要能解释（解释方向: 复用同一套 ADMIN 鉴权语义，避免为只读统计开第二个信任通道）。

**结论: 不是"被 AI 写乱的架构"。** 边界一致、依赖单向、惯例统一（每个服务同构的 AdminAccess/审计/异常模式），dev-log 与代码三方可互证。上面三个问题是设计取舍级别，不是混乱。

---

## 第三部分：核心业务链路审查

### 不变量保护核对表

| 不变量 | 有保障? | 保障位置 | 缺口 |
|---|---|---|---|
| 库存不为负 | ✅ | `InventoryRepository.deductAvailableStock` 条件 UPDATE `available >= :qty`；0 行更新→`Insufficient inventory` | 无 |
| 同订单不重复扣减 | ✅ | `uk_inventory_records_order_change(order_no, change_type)` 唯一键 + `changeInventory` 先查重放 | check-then-act 有竞态窗口，但唯一键兜底，事务回滚 |
| 释放不超锁定量 | ✅ | `releaseLockedStock` 条件 `locked >= :qty` | 无 |
| 重复下单幂等 | ✅ | Redis `setIfAbsent` 锁 + `uk_orders_idempotency_key` + DataIntegrityViolation 重放（OrderCommandService:104-110） | 无 |
| 用户不能动别人订单 | ✅ | `findByOrderNoAndUserId`（cancel）、payment `filter(userId.equals)` | 无 |
| 已取消订单不可支付 | ✅ | `PaymentCommandService.validatePayable` + `OrderStateMachine` | 无 |
| 重复支付/重复消费 | ✅ | payment 唯一约束重放；两个 MQ 消费者 eventId 查重 + 唯一约束双保险 | MQ 无死信队列，失败消息处理依赖日志 |
| 管理员操作可审计 | ✅ | 所有写路径经 `AdminAuditWriter`（adjust/inbound/suggestion reject 均带 before/after 快照、IP、UA、requestId） | 无 |
| AI 不能直接改库存 | ✅ | AI 包无写依赖；唯一增库存路径 `InboundOrderDraftService.confirm` 要求 ADMIN + `X-Request-Id` 幂等（:170-209） | 无 |
| AI 建议不能替代审批 | ✅（后端） | 建议→`convertToInboundDraft`→草稿→`confirm` 三步都要 ADMIN，状态机防跳步 | **无 UI 呈现此流程** |
| AI 建议可追踪可解释 | ⚠️ 部分 | V12 元数据列 + `linked_inbound_no` + 审计 `AdminAuditSourceType.AI_SUGGESTION` | **生成建议的代码（Task 8）还不存在**，链路有表无源头 |
| 重复确认入库不重复加库存 | ✅ | `findByConfirmRequestId` 幂等 + 状态检查（InboundOrderDraftService:171-189） | 无 |

通知链路: 发布在支付事务内（`PaymentEventPublisher.publishSuccess` 在 `@Transactional pay` 中）——若事务提交失败但消息已发，存在"虚假支付成功事件"理论窗口；无 outbox 模式。课程项目可接受，面试需能说出这个词。

---

## 第四部分：AI 库存助手中期审查（核心）

### 已实现什么（基于代码而非任务状态）

1. **真实 LLM 调用链**: 模板（系统提示含 8 条守则）→ `AiProviderRequest`（system+user 消息、注入序列化输入快照）→ DeepSeek/MiniMax（OpenAI 兼容协议、strict JSON mode、超时分类、错误五分类）→ 输出校验 → 仅返回校验后内容。
2. **反幻觉校验器**（`AiModelOutputValidator`）: productId 白名单、productName/库存数/7日销量必须逐值等于输入快照、日期值白名单、数字非负、SQL 正则拦截、内部端点/信任头/secret 正则拦截、"库存已变更"声明拦截（文本模式 + 8 个禁止字段名）。这远超"伪 AI 包装"。
3. **证据聚合门面**: 低库存候选（仓储级查询 `available <= safetyStock and safetyStock > 0`）+ 每商品近 7 天付费销量（跨服务取证）+ 近 N 条库存流水 + 显式 limitations（无销量证据/无流水/截断说明）+ 数据时间窗。
4. **Q&A**: 关键词白名单意图检测（中英文）→ 4 类只读意图 → 证据 + LLM 摘要 → unsupported 受控响应。这是 PRD 明确要求的设计（"backend whitelist"），不是偷工减料。
5. **低库存分析端点**（7.1）+ 4 个证据端点，全部 `AdminAccess.requireAdmin()` + 网关 ADMIN 双重校验，MockMvc 覆盖 401/403/成功/无副作用。
6. **审批-执行后半程（Phase 2.5 遗产）**: 建议 reject/convert 幂等 + 审计；入库草稿→确认→事务内加库存+流水+审计→APPLIED；requestId 幂等。

### 关键问题逐条回答

- **触发方式**: 管理员经网关调 REST（Q&A 是 POST 提问；分析是 POST 端点）。无定时任务、无自主触发。✅ 符合 PRD。
- **输出是否落库**: **否**。Q&A 和低库存分析结果只返回不落库。`ai_operation_suggestion` 表和全套元数据列已就绪，但**没有任何生产代码写入它**（Task 8 未做）。这是当前最大断裂点: 审批流是"有审批无来源"。
- **是否进入审批流/管理员确认后能否执行**: 后端 API 全通（reject/convert/confirm），前端全无。
- **分析过程有无日志审计**: 建议生命周期有完整审计；但分析调用本身（谁在何时问了什么、模型答了什么）不持久化——只有最终落库的建议会带 prompt_version/model 元数据。
- **AI 输出无法落地?** 不是"无法"，是"还没接"——分析响应里有 riskLevel/reason/items，与建议表字段一一对应，Task 8 是纯粹的"把已校验输出写进已建好的表"。

### 类型判定

**当前实现 = D（有上下文聚合的 AI 分析助手）已成立，E（可落库、可审批、可执行的补货建议系统）完成约 70%——两头都好了，中间"生成→落库"一环缺失。**

证据: 真实 provider HTTP 调用（OpenAiCompatibleAiProvider:46-53）、多源证据聚合（EvidenceFacade 跨服务取销量）、结构化输入输出 + schema 版本、严格服务端校验——不是 A/B/C。但意图识别是关键词规则（合规但简单），MOCK provider 返回空 items（MockAiProvider:65 `payload.put("items", List.of())`），意味着**离线演示走不通完整链路**。

### 是否需要重构？

**判定: 第 1 类——方向正确，继续补完。不要推翻，不要局部重构。**

- **保留**: 全部。Provider 抽象、validator、证据门面、Phase 2.5 审批链、契约文档体系。
- **应重构**: 无结构性重构必要。唯一建议改动是 `MockAiProvider`: 让它基于输入快照生成合理 items（取 evidence 里真实 productId、`suggestedQuantity = max(safetyStock*2 - available, 1)` 之类的确定性规则），否则 MOCK 模式下 Task 8 产出的建议永远没有条目、`convertToInboundDraft` 直接抛 "no items to convert"。
- **应删除/合并**: 无。Q&A 双测试文件（`AiInventoryQuestionServiceTest`/`AiInventoryQuestionAnalysisServiceTest`）分工不同（意图层 vs 分析接线层），保留。
- **马上补齐**: Task 7.2 → 8 → 9（后端闭环），然后 Task 10（UI）。
- **暂缓**: Task 11 日报、滞销分析、趋势图。
- **不建议扩展**: 自主 agent/工具循环、独立 ai-assistant-service、向量检索/RAG。

---

## 第五部分：admin-safe 审查

**现状**: 已是真正的后台体系，不是接口复用——独立 `adminLogin`（拒绝非 ADMIN）、网关 + 下游双层 ADMIN 校验（8 个 Admin 控制器全部本地 `requireAdmin`，已逐一核对）、独立 admin DTO（`AdminInventoryResponse` 等，不暴露实体）、统一审计写入、`/internal/**` 网关封锁 + `InternalAuthFilter` 秘钥校验、admin-frontend 完全独立工程。

**缺口**:

1. （中）`UserContextFilter` 的 legacy 回退: `trustsPropagationHeaders` 在 `internalSecret` 未配置时无条件信任 `X-User-*` 头（common-auth `UserContextFilter`，注释自承"legacy behaviour"）。compose 把 `MINIMALL_INTERNAL_TOKEN` 设为必填且服务端口不发布，所以容器化部署安全；但裸跑服务（本地 `mvn spring-boot:run` 不带该变量）时任何能摸到服务端口的人可伪造 ADMIN。建议至少在 README 加显著警告，或改为"未配置即不信任"。
2. （中）AI 审批 UI 缺失: 后台无法承接补货单审批——不是权限缺口，是功能缺口（Task 10）。
3. （低）`AdminAccess` 在 6 个服务各复制一份: 刻意的去共享耦合，可辩护，无需改。

**不建议过度设计**: 不要做菜单/按钮级权限、多租户、审批工作流引擎——PRD 明令禁止且对展示无益。

---

## 第六部分：安全风险分级

**High（GitHub/演示前必须修）: 未发现。** 专项核对过: 无硬编码密钥（`.env` 被 gitignore 且未被 git 跟踪、`.env.example` 全是 change-me 占位、AI key 走 `AI_API_KEY` 环境变量）；JWT 强制 secret、角色进 claim、过期校验；CORS 默认仅 localhost 且 credentials=false；无 IDOR（订单/支付都绑 userId）；AI 端点双层 ADMIN；validator 阻止输出泄露内部信息。

**Medium（正式展示前建议修）**:

| # | 问题 | 位置 | 风险 | 建议 |
|---|---|---|---|---|
| M1 | internal secret 未配置时信任伪造头 | `common-auth/.../UserContextFilter.trustsPropagationHeaders` | 非 compose 部署可伪造 ADMIN | fail-closed 或文档高亮 |
| M2 | 订单创建 saga 缺口（先扣后存） | `OrderCommandService.createNewOrder:121` | 锁定库存泄漏 | 至少 README 已知限制；或加孤儿释放兜底任务 |
| M3 | payment 直读 orders 表 | `payment/domain/Order.java` | 边界违规 + schema 漂移风险 | 改为调 order-service 接口，或文档明示取舍 |
| M4 | 业务数据外发第三方 LLM | `OpenAiCompatibleAiProvider` | 库存/销量数据出境到 DeepSeek/MiniMax | README 声明 + 演示默认 MOCK |
| M5 | 契约漂移: low-stock-analysis 契约写 GET、实现为 POST | `phase3-ai-inventory-contract.md:173` vs `AdminAiInventoryAnalysisController` | 违反契约文档自己的更新规则 | 改契约文档（一行事） |

**Low**: 限流 fail-open 默认值；MQ 无死信队列；`X-Forwarded-For` 取第一段可被伪造（仅影响审计 IP 字段）；JWT 无刷新/吊销机制（1h 过期可接受）。

---

## 第七部分：代码质量与测试

**质量判定: 高于典型 AI 生成项目一大截，未见失控膨胀。** 文件数多但单文件职责单一（最大服务类 341 行）；控制器全部薄壳；命名体系全局一致；DTO 有 6 种 evidence 形态的轻度繁殖但各有结构差异，合并收益低；**未发现任务树标 done 而代码缺失的情况**（逐项抽查 Task 2/3/4/5/6/7.1 均有对应代码+测试+dev-log 三方一致）。测试不是形式化的: 88 个 Java 测试类 + 14 个前端 spec，controller 层验证的是 401/403/无副作用/无审计写入这类行为断言。

**必要复杂性**: provider 抽象（3 实现）、validator、prompt 版本化——全有 PRD 依据。**暂不要动的**: AdminAccess 复制、evidence DTO 家族、双 Q&A 测试。

**验收能力缺口**: ① 迁移手工执行（V1–V12 逐个 docker exec，无 Flyway）；② 无种子数据（`infra/mysql/init/` 只有 README）；③ 无演示脚本；④ AI 演示在 MOCK 下空 items、在 compose 下 AI_* 变量未透传（`x-java-service-environment` 锚点无 AI_*，真实 provider 起不来）；⑤ 无跨服务端到端测试。

---

## 最终结论

### 1. 总体结论

**较完整简历项目**（介于"较完整简历项目"与"接近企业级展示项目"之间）。一句话: **工程纪律和安全边界已达到展示级，但 AI 助手"有审批无来源、有后端无界面"，闭环差最后三个任务，当前最大风险是"演示不出来"而不是"做得不对"。**

### 2. 强项（真实有价值的）

- 网关信任模型完整且被测试锁定（伪造头剥离、双层 ADMIN、内部路径封锁、内部令牌）
- 库存/订单/支付三处幂等全部有 DB 唯一约束兜底，不变量保护齐全
- AI 反幻觉校验器是同类项目罕见的真实防线（逐值核对输入快照）
- 契约先行 + TaskMaster + dev-log 的可追溯开发过程，本身就是面试素材
- 全链路审计（before/after 快照 + AI_SUGGESTION 来源标记）

### 3. 主要短板

- AI 闭环断点: Task 8（建议落库）未做 → 审批流无米下锅
- admin-frontend 零 AI 页面、零入库单页面 → 闭环无法可视化演示
- MOCK provider 空 items + compose 不透传 AI 变量 → 离线/容器演示双双受阻
- 无种子数据、迁移手工 12 连跑 → 他人无法 30 分钟内复现
- payment 直读 orders 表、lockedStock 永不消耗 → 两个必须能自圆其说的设计点

### 4. AI 助手闭环缺口清单

| 缺口 | 状态 | 影响演示 | 影响简历可信度 | 优先级 | 修法 |
|---|---|---|---|---|---|
| 建议生成落库（Task 8） | 表/校验/元数据全就绪，差 service+endpoint | **是** | **是**（没有它就是"分析玩具"） | **P0** | 按契约 §7 实现 generate 端点，复用 AnalysisService+REPLENISHMENT 模板 |
| hot-product 端点（7.2/7.3） | evidence 就绪，差端点 | 是 | 中 | P0 | 照抄 7.1 模式 |
| APPLIED 状态同步（Task 9） | confirm 流就绪，差建议状态回写 | 是 | 是 | P0 | 在 confirm 事务内回查 `linked_inbound_no` |
| Mock items 为空 | `MockAiProvider:65` | **是** | 否 | **P0** | 从输入快照确定性生成 items |
| AI 前端页面（Task 10） | 零 | **是** | **是** | **P0.5** | `/ai-inventory` + 建议审批 + 入库单三页 |
| compose AI_* 透传 | 缺 | 是（真实 LLM 演示） | 否 | P1 | 锚点加 7 个变量 |
| 演示种子数据（Task 12） | 零 | **是** | 否 | P1 | dev-profile 确定性生成器 |
| 分析调用历史持久化 | 无 | 否 | 中 | P2 | 轻量 analysis log 表，PRD P1 范畴 |
| 契约 GET/POST 漂移 | 文档未更新 | 否 | 中（自相矛盾） | P0（5 分钟） | 改 `phase3-ai-inventory-contract.md` |

### 5. 重构路线图

- **P0（立刻，全是收尾不是返工）**: 修契约漂移 → Task 7.2/7.3 → 增强 MockAiProvider → Task 8 → Task 9。全部在 inventory-service 内，无架构改动。
- **P1（展示前）**: Task 10 三个页面 → 种子数据（Task 12 提级）→ compose AI 变量 → README 加"已知限制"节（M1–M4）→ 端到端演示脚本。
- **P2（有时间）**: 分析历史表、日报（Task 11）、Flyway 化迁移、MQ 死信队列。
- **不建议现在做**: 拆 ai-assistant-service、payment 改 HTTP 调用（动核心链路风险大于收益，文档说明即可）、RBAC 细化、自主 agent、RAG。

### 6. AI 助手后续完成路线

- **最小可演示版（约 3–4 个任务量）**: 7.2 → Mock 增强 → 8 → 9 + 用 curl/HTTP 文件演示后端闭环 + 审计日志页面佐证。
- **完整版**: 上述 + Task 10 三页面 + Task 12 种子数据 + Task 13 回归验收。
- **顺序**: 严格按 TaskMaster 既定顺序走即可——这个 PRD 的任务切分本来就是对的。

### 7. 模型分工建议

- **Codex/Sonnet（机械实现）**: Task 7.2/7.3（有 7.1 完整范本）、契约文档修正、compose 变量、Mock 增强——模式明确、验收标准清晰。
- **Opus/Fable（需要判断）**: Task 8（涉及"无效输出是否落库"的契约裁量）、Task 9（事务边界内回写建议状态）、Task 10 页面信息架构。
- **人类先决策**: M1 fail-closed 还是文档化；M3 payment 表共享是否改；演示用真实 provider 还是 MOCK；简历措辞。
- **不应交给 AI 自动执行**: 数据库迁移在真实库上的执行、git commit（项目规则要求逐次授权）、对外发布。

### 8. 演示流程建议

1. 顾客端下单→支付→订单状态流转（展示幂等: 重复提交同 idempotencyKey）
2. 管理端登录（展示 USER token 调 admin 接口得 403）
3. 库存页看扣减流水
4. AI 页: 先问答（演示 unsupported 受控响应）→ 低库存分析（指出数据时间窗 + limitations + 证据并排）→ 生成补货建议
5. 审批: 驳回一条（看审计日志）、转化一条 → 入库草稿 → 确认 → 库存增加 + 流水 + 建议变 APPLIED
6. 审计日志页展示全链 AI_SUGGESTION 来源追踪
7. 架构讲解顺序: 网关信任模型 → 库存不变量 → AI 权限边界（"AI 只能建议，唯一加库存入口是管理员确认"）→ 反幻觉校验

### 9. 简历写法

> 基于 Spring Boot 3 / Spring Cloud Gateway 的微服务电商系统（7 服务，Docker Compose 编排），实现订单-库存-支付链路的幂等与一致性保护（条件更新防超卖、唯一约束兜底幂等、MQ 消费幂等）；设计网关统一鉴权与服务间信任传播模型（伪造头剥离 + 内部令牌）；在库存服务上构建 LLM 辅助的补货建议系统: 多源证据聚合 → 版本化 Prompt → 多 Provider 适配（DeepSeek/MiniMax/Mock）→ 服务端逐字段反幻觉校验 → 建议落库 → 管理员审批 → 幂等执行 → 全链审计，AI 全程无库存写权限。

边界要诚实: 是"LLM 辅助分析 + 人工审批"，不是预测算法；意图识别是白名单规则；学习型项目非生产系统。

### 10. 面试追问与回答方向

- **AI 智能在哪，为什么不是规则系统？** 规则只能输出"低于安全库存"；LLM 综合销量趋势、流水、缺数据情形生成解释性建议与风险分级；但校验是规则的——"生成靠模型、守门靠代码"，这句话就是设计核心。
- **AI 建议如何防止越权执行？** 三重: AI 代码无写依赖（编译期保证）、唯一加库存入口要求 ADMIN 上下文 + requestId、网关/下游双层鉴权有回归测试锁定。
- **如何保证库存一致性？** 条件 UPDATE 原子扣减 + (order_no, change_type) 唯一键 + 乐观锁版本；坦承 saga 缺口（M2）并说出补偿思路。
- **开发到一半如何判断要不要重构？** 本次审查就是答案: 对照锁定契约逐项核对实现、检查闭环断点位置、确认断点是"未完成"还是"做错了"——结论是补完不推翻。
- **哪些是你设计的？** 契约边界、信任模型、校验规则集是人决策；逐任务实现有 AI 辅助但每步有测试验收和 dev-log，这套"人定边界、AI 实现、测试守门"的流程本身值得讲。
- **需准备好的硬问题**: 为什么微服务共库（答: 演示成本取舍，表集逻辑隔离，payment 读表是已识别债务）；lockedStock 语义（答: 无发货环节的建模简化）。

### 未能确认，需要进一步检查

1. order-service 销量聚合的排序/口径实现细节（未读 `AdminOperationStatsController` 内部）
2. MiniMaxAiProvider 特有 payload 校验
3. Phase 1/2 验收文档声称的回归本次未重跑
4. `pressure/` 压测脚本是否仍可用

---

## 最终判断

**选 F（继续完成 AI 库存助手，但限定边界）为主，辅以 G 的一小部分（演示链路整理）。**

理由: 架构没有乱（B 不需要）；核心业务漏洞均为 Medium 且有兜底（C 不紧迫）；后台安全体系已成型（D 已基本完成）；AI 方向经逐文件核对是对的、断点清晰且全部是"未做"而非"做错"（E 不成立）。**最大风险是闭环差三个任务导致演示和简历可信度落空**——按既有 TaskMaster 顺序把 7.2→8→9→10 收完、补上种子数据和 Mock 增强，这个项目就从"代码很好但展示不出来"变成"完整可信的 AI 应用展示项目"。继续堆新功能（A）是唯一明确错误的选项。
