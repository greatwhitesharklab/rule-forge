# V5.22 — AI 规则创作助手

V5.22 给业务分析师(BA)配了一个 AI 助手,直接在 console-ui 的 AI 助手面板里对话、查规则、改规则、看健康。

## 它解决什么

BA 日常三件痛事:

1. **配置规则** — 写决策表 / 评分卡 / 决策流手写 JSON 容易出错
2. **测试写好的规则** — 写完不跑不知道对不对
3. **观察已部署规则** — 跑起来后哪些死规则 / 哪些滞留草稿 / 哪里异常

V5.22 把这三件事全部接到 AI 助手里。

## 主要特性

### 1. AI 对话生成规则

在 AI 助手面板输入自然语言,LLM 自动调 `draft_rule` 工具把规则存到 `rf_draft` 表,返回 `draftId`。BA 在草稿 tab 里看 / 改 / 提交审批。

支持的 9 种规则类型: `decision_table`、`ul`、`script_decision_table`、`decision_tree`、`scorecard`、`complex_scorecard`、`decision_flow`、`ruleforge`、`crosstab`。

### 2. 草稿生命周期管理

`rf_draft` 表状态机:

```
DRAFT  ──submit──→  PENDING_REVIEW  ──approve──→  APPROVED  ──apply──→  (写入主存储)
  │                       │
  │                       └──reject──→  REJECTED  (reviewComment 记原因)
  │
  └──expire──→  EXPIRED  (7 天后自动)
```

LLM 只能建 `DRAFT`;`PENDING_REVIEW` / `APPROVED` / `REJECTED` 都是 BA 手动操作(防止 LLM 误审批)。`apply` 是一步到位:APPROVED → 写主存储,生成新版本。

### 3. 测试用例持久化

V5.22.1 把原来跑一次就丢的 `generate_test_cases` 升级为持久化测试用例。`rf_draft_test_case` 表存所有 saved tests,BA 可手动增 / 删,LMM 可自动生成。`run_saved_tests` 跑所有 saved 拿 PASS / FAIL 跟期望行 ID 比对。

### 4. 规则健康仪表盘

V5.22.2 在 AI 助手面板加了"健康" tab。聚合 5 个数据源:

- 覆盖率卡片(总 / 活跃 / 死规则)
- 滞留草稿(> 3 天的 DRAFT / PENDING_REVIEW)
- 最近异常事件
- 热规则 Top 5
- Top 拒绝原因

时间窗口可切 7 / 30 / 90 天。

### 5. 审计 + 限流

每次 `agent/tools/{name}` 调用都会写一行 `nd_agent_audit` (app_db):

- session_id, message_id, user_id, tool_name
- args_summary (截 500 字符), result_size
- status: OK / ERROR / RATE_LIMITED
- error_code, error_message, duration_ms

每个 user + session 独立限流:**100 calls / hour** 滑动窗口(配置项 `ruleforge.agent.rate-limit.max-per-hour`)。超限返 429。

## 数据库迁移

| 版本 | 表 | 权限域 | 用途 |
|---|---|---|---|
| V5.22.0 | `rf_draft` | ruleforge_db | AI 草稿主表 |
| V5.22.1 | `rf_draft_test_case` | ruleforge_db | 持久化测试用例 |
| V5.22.2 | `nd_agent_audit` | app_db | 工具调用审计 |

## CLI(给 LLM agent 用)

`cli/bin/ruleforge.ts` 提供 12 个子命令:

```bash
# Rule schema
ruleforge rule list-types
ruleforge rule get-schema --type decision_table

# 草稿生命周期
ruleforge rule draft --rule-type decision_table --project demo --content-file rule.json
ruleforge rule list-drafts --project demo
ruleforge rule get-draft --draft-id drf_abc
ruleforge rule submit --draft-id drf_abc
ruleforge rule approve --draft-id drf_abc --reviewer BA1
ruleforge rule reject --draft-id drf_abc --reviewer BA1 --reason "..."
ruleforge rule apply --draft-id drf_abc --package-path /demo

# 测试用例
ruleforge rule test-gen --draft-id drf_abc
ruleforge rule test-run --draft-id drf_abc --test-cases-file tcs.json
ruleforge rule list-tests --draft-id drf_abc
ruleforge rule add-test --draft-id drf_abc --name "under18" --inputs '{"customer.age":17}' --expected-row-id r1
ruleforge rule del-test --test-case-id tc_1
ruleforge rule run-saved-tests --draft-id drf_abc

# 规则健康
ruleforge rule health --project demo --days 30
```

## REST API

`POST /ruleforge/agent/tools/{name}` — body 是工具参数,返 JSON 结果。

支持 14 个工具:

| 工具 | 用途 |
|---|---|
| `draft_rule` | 创建 AI 草稿 |
| `list_drafts` | 列草稿 |
| `get_draft` | 取详情 |
| `submit_draft` | 提交审批 |
| `reject_draft` | 拒绝 |
| `approve_draft` | 审批通过 |
| `apply_draft` | 写入主存储 |
| `generate_test_cases` | 生成测试模板 |
| `run_test` | 跑测试(不落库) |
| `list_test_cases` | 列已保存测试 |
| `add_test_case` | 加测试 |
| `delete_test_case` | 删测试 |
| `run_saved_tests` | 跑已保存测试 |
| `get_rule_health` | 规则健康仪表盘 |

## 限流响应

超 100 calls/hour 返:

```json
HTTP 429
{
  "error": "rate_limit_exceeded",
  "message": "用户 user1 超过每小时 100 次调用上限",
  "maxPerHour": 100
}
```

## 测试覆盖

- 后端: 70 个单元测试 pass(决策表 / 测试用例 / 限流 / 审计 / ToolExecutor)
- CLI: 45 个测试 pass(12 V5.22 + 5 V5.22.1 + 3 V5.22.2)
- 前端: 340 个 vitest pass(决策表 / 健康仪表盘)

## V5.22.3 — 7 项 polish

V5.22.3 是个 patch 级别,7 项改进全在 BA 视角:

| # | 改进 | 影响面 |
|---|---|---|
| 1 | `rf_draft_history` 表 + 时间线 UI | 草稿审计 |
| 2 | 健康仪表盘加 antd `Progress` 进度条 | 视觉化 |
| 3 | "审计" tab + 工具调用历史 | BA 自我回顾 |
| 4 | rate limit 429 带 `retryAfterSeconds` | API 体验 |
| 5 | 健康视图 DEGRADED / PARTIAL 状态标记 | 故障可见性 |
| 6 | CLI `rule health --format=table` | CLI 可读性 |
| 7 | VitePress 截图 | 文档 |

### 1. 草稿状态历史

每个草稿点"状态历史" tab 看完整时间线:

```
●  CREATE  by LLM         2026-06-09 10:00
  │
  ●  SUBMIT   DRAFT → PENDING_REVIEW   by BA1   2026-06-09 10:05
  │
  ●  REJECT   PENDING_REVIEW → REJECTED   by BA2   2026-06-09 10:10
  cellMap 的 row 2 缺 condition
  │
  ●  EDIT     REJECTED → REJECTED   by BA1   2026-06-09 10:15
  (注:此处"修一下"是 REJECTED 上的手动改,不改 status)
```

### 2. 健康仪表盘加进度条

- 活跃规则 / 死规则从裸数字 → 进度条(直接看出占比)
- 热规则 Top 5 → 相对 max 触发量的水平条
- 拒绝原因 Top 5 → 相对 max 次数的水平条

### 3. 工具调用历史(新 tab)

AI 助手面板加第 4 个 tab **审计**。BA 看自己今天调了什么:

```
范围: [BA1 ▾]    状态: [全部 ▾]                3 条记录   ⟳

[ list_drafts ]  [OK]   42ms · 100B           2026-06-09 10:00
  by BA1  · 会话 sess_abcd
  {"project":"demo"}

[ draft_rule ]   [ERROR] 5ms · -B             2026-06-09 10:01
  by BA1  · 会话 sess_abcd
  ❌ tool_execution_failed  content 不是合法 JSON

[ get_rule_health ]  [限流] 0ms · -B          2026-06-09 10:02
  by BA1  · 会话 sess_abcd
```

### 4. rate limit 响应

```json
HTTP 429
{
  "error": "rate_limit_exceeded",
  "message": "用户 BA1 超过每小时 100 次调用上限",
  "maxPerHour": 100,
  "retryAfterSeconds": 2847
}
```

客户端按 `retryAfterSeconds` 设个 `setTimeout` 就行,不用等 60 分钟。

### 5. DEGRADED 状态标记

健康仪表盘全部 sub-source 都炸时(罕见),顶部红色横幅:

```
❌ 健康数据源全部不可用 — 显示空为正常,稍后重试
   失败来源: [coverage] [hotRules] [recentAnomalies] [topRejectReasons] [staleDrafts]
```

部分失败时 PARTIAL 黄色 + 列出哪些 source 失败。

### 6. CLI `--format=table`

```bash
$ ruleforge rule health --project demo --days 7 --format=table

✅ 状态: OK
项目: demo · 时间窗口: 7 天 · 更新于 2026-06-09 18:15

--- 覆盖率 ---
  总规则: 50 · 活跃: 35 · 死规则: 15

--- 热规则 Top ---
  r_hot  1000  次
  r_warm 500  次

--- Top 拒绝原因 ---
  AGE_TOO_LOW  120  次
  INSUFFICIENT_INCOME  80  次

--- 滞留草稿 ---
  ✅ 无
```

JSON 还是 default,`--format=table` 走 pretty print。

### 7. 截图

`console-ui/e2e/screenshots/` 目录已经在 V5.22 phase 7 用 Playwright 截了"草稿 tab" 和 "健康 tab" 截图。新加的 V5.22.3 UI(状态历史 / 审计 / DEGRADED 横幅)截图将进 V5.22.4。
