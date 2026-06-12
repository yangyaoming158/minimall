# AI 库存助手真实模型冒烟测试问题与修复规划

- 日期：2026-06-12（测试执行于 2026-06-11 晚）
- 环境：docker compose 全栈 + `AI_PROVIDER=DEEPSEEK`（`deepseek-v4-flash`）+ Phase 3 演示数据
- 背景：Phase 3 验收（13/13）后的首次真实 LLM 端到端冒烟。MOCK 路径全绿；以下问题
  均只在真实模型下暴露。防幻觉校验器全程按契约正确拦截，不属于缺陷，本规划不改动
  其任何既有断言（只允许加严）。

## 问题清单与修复方案

### P1. 低库存分析必现「校验失败：productName is not in input snapshot」

- 现象：点击低库存分析，模型输出被后端拒绝，UI 显示校验失败。
- 根因：`low-stock-analysis-v1` 的 taskPrompt 要求 items 包含 `productName`，但
  inventory-service 的输入快照没有商品名（`productFacts.productName = null`）。
  模型被迫编造，校验器按「事实必须等于输入快照」正确拒绝。Prompt 自己诱导了违规。
- 修复：prompt v2 改为「快照中不存在/为 null 的字段必须省略，不得猜测」。
  四个模板（qa / low-stock / hot-products / replenishment）同步检查。

### P2. 问答偶发「limitations must be an array」

- 现象：带商品 ID 提问时模型输出被结构校验拒绝。
- 根因：prompt 未强制 `limitations` 的类型；DeepSeek 有时返回字符串。
- 修复：prompt v2 明确「`limitations` 必须是 JSON 字符串数组；没有局限时输出 []」。
  校验器不放松。

### P3. 问答「PH3-AI-LOW-TEA 当前库存多少」报 productId is required

- 现象：把商品号写进问题文本无效，报 40000 英文错误。
- 根因：问答意图识别是规则匹配，CURRENT_STOCK / PRODUCT_STATUS / RECENT_RECORDS
  意图强制要求单独的 `productId` 字段（`AiInventoryQuestionService.requireProductId`），
  不从问题文本提取。
- 修复：productId 字段为空时，从问题文本中提取形如 SKU 的 token（如
  `[A-Za-z][A-Za-z0-9-]{2,63}`），与库存表比对命中则采用；仍缺失时返回中文引导文案。
  前端 placeholder 同步改为不误导的提示，并对该错误做友好映射。

### P4. 中英文夹杂，对使用者不友好

- 现象：分析摘要、建议说明、数据范围、数据局限大量英文。
- 根因：① 四个 prompt 均未规定输出语言，DeepSeek 默认英文；② 后端硬编码英文文案——
  证据 limitations（"Sales evidence is limited to..."）、建议 `inputSummary`
  （"Replenishment evidence: ..."）、日报 limitations（`AiDailyInventoryReportService`）。
- 修复：① prompt v2 要求所有自然语言字段（summary/answer/reason/limitations）使用
  简体中文；② 上述后端硬编码文案全部中文化（仅文案，不改结构与字段名）。

### P5. 同一库存状态下两次生成的建议差异大（46 件 vs 40 件；字段时有时无）

- 现象：相邻两次生成，HOT-MUG 建议补 30 vs 24；一次回显可用库存、一次为空；理由列
  时有时无。
- 根因（三层）：① provider 请求体未设置 `temperature`，DeepSeek 默认 ~1.0，输出按
  采样波动；② prompt 未给补货数量定规则，数量是模型自由判断，校验器对它只有
  「正整数」约束（它不是输入快照里的事实，等值校验管不到）；③ 事实回显字段在
  schema 中可选，模型可填可不填。
- 修复：① `OpenAiCompatibleAiProvider` 请求体加 `temperature`，环境变量
  `AI_TEMPERATURE` 配置、默认 0（compose / .env.example 同步）；② prompt v2 写入
  数量锚定公式「建议补货量 = max(2×安全库存 − 可用库存, 近 7 天销量)」并给出上限；
  校验器**新增**上限断言（数量 ≤ 由快照推导的合理上限，加严不放松）；③ prompt v2
  强制 items 原样回显快照中存在的全部事实字段及 reason。
- 预期：temperature=0 不承诺逐字节可复现，但数量由公式锚定 + 校验器上限兜底后，
  相邻两次结果应收敛一致。

## 附带观察（并入 P5 验证）

- 热销分析「分析明细」中可用/安全有值但销量、风险、说明为「—」——即 P5③ 的可选
  回显问题，修复后应消失。
- 补货建议把库存健康的热销品（HOT-MUG，可用 42 > 安全 10）也列入补货——模型判断
  层面的激进，不违反校验；P5② 公式锚定后应只在覆盖天数不足时出现。

## 实施与验证

- 实施顺序：prompt v2（含 promptVersion 升版）→ provider temperature → 问答提取 →
  文案中文化 → 前端文案。校验器只加不减。
- 回归：MockAiProviderTest / AiModelOutputValidatorTest / 分析与生成控制器测试 /
  Phase3AiLoopAcceptanceTest 全量；admin-frontend vitest + build + 网关审计；
  真实 DeepSeek 手动复测五个场景各两次（验证收敛性与全中文输出）。
- 边界：不改 `/api/admin/ai/**` 契约路径与响应结构；不触碰建议/入库状态机；
  不削弱任何防幻觉断言。
