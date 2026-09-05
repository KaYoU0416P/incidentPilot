# Agent 设计

## Router

输出 `DIRECT | RETRIEVAL | AGENTIC`、置信度和简短可审计理由。规则优先处理明显场景，模型分类用于语义边界；低置信度默认走 Retrieval，而不是高成本 Agentic。

## 有界 Agent Loop

```text
initialize state -> model decision -> validate tool call -> execute
-> append bounded observation -> evidence check -> answer or next step
```

循环必须有 max steps、总 deadline、单工具 timeout、上下文预算、重复调用检测和 terminal reason。工具失败只在错误可重试且剩余预算允许时重试；参数错误不重试。

## Tools

- `searchKnowledge`
- `queryIncidentHistory`
- `queryDeployment`
- `queryServiceStatus`
- `queryChangeLog`

所有 MVP 工具只读、输入使用结构化 schema、输出限制条数/字段/字节，并记录工具名、脱敏参数、耗时、结果数量和状态。工具返回数据是不可信内容，不得把其中指令当系统指令执行。

## Evidence Verification

最终结构化输出的关键事实必须映射到 Evidence。Verifier 检查引用存在性、来源类型、时间一致性和最小证据数量；它不声称完成形式化事实证明。

## MCP

MCP 延后到核心稳定后。若实现，只暴露已有 Tool port，不复制业务逻辑。MCP Server/transport/permission 的选择需独立 ADR 和端到端验证。

## 未决参数

max steps、timeout、context budget、重试次数、路由阈值均在实现与负载测试后确定，不能把占位值宣传为生产最优。

## 执行预算设计（2026-09-05 加固版）

上一版只在两次工具调用之间检查 deadline，单次工具调用和最终回答可以任意超时，上下文也只是无上限地累加字符串。加固版把预算显式建模为一等对象并在整条链路传播。

`AgentBudget` 参数（可通过 `incidentpilot.agent.*` 覆盖）：

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| `max-steps` | 5 | 工具调用步数上限 |
| `total-budget` | 20s | 从进入 Agent 到返回的总墙钟预算 |
| `tool-timeout` | 6s | 单个工具调用上限 |
| `answer-reserve` | 8s | 为最终回答保留的时间；剩余预算低于它就停止继续调用工具 |
| `context-chars` | 6000 | 工具事实进入 prompt 的总字符预算 |
| `fact-chars` | 600 | 单条工具事实字符上限 |
| `fact-lookback` | 90d | 动态事实工具的显式时间窗口 |

传播与取消：

- 每个工具在虚拟线程 executor 上执行，超时时间取 `min(tool-timeout, remaining)`。
- 超时触发 `Future.cancel(true)`，工具线程被中断，trace 记 `TIMEOUT`，该工具不产出证据。
- 循环在 `steps >= max-steps`、`remaining <= answer-reserve` 或上下文预算耗尽时停止，terminal reason 分别为 `MAX_STEPS`、`DEADLINE_EXCEEDED`、`CONTEXT_BUDGET_EXHAUSTED`。
- 最终回答同样在 executor 上执行并受 `remaining` 约束，超时 terminal reason 为 `ANSWER_TIMEOUT`，不返回半成品答案。

预算是墙钟约束，不是 SLA。工具线程被中断后，底层 JDBC/HTTP 调用是否立即返回取决于驱动，本项目只保证请求线程不再等待、结果不再进入上下文。

## 工具结果契约

```text
ToolStatus = SUCCESS | EMPTY | SKIPPED_MISSING_PARAMETER | TIMEOUT | FAILED
ToolFact   = { text, source, observedAt }
ToolResult = { tool, status, facts, note }
```

只有 `SUCCESS` 的 `ToolFact` 才会编号成 `T1...Tn` 证据。`EMPTY`（查到 0 行）、`SKIPPED_MISSING_PARAMETER`（缺少 serviceName，拒绝宽表扫描）、`TIMEOUT`、`FAILED` 只写入轨迹的 `status` 和 `note`，绝不作为事实进入 prompt。上一版把“serviceName 未提供，未执行宽表扫描”当作有效事实交给模型，加固版已消除。

动态事实工具必须带显式时间窗口（`changed_at >= now() - lookback`），并在 fact 文本中携带来源表和 UTC 时间戳，使证据可追溯。

## Route 行为

Router 先判定企业事实信号，再判定解释型问题，避免“payment-service 的 5xx 是什么原因”这类包含企业事实的问题因为“是什么”被直答：

1. 命中服务名 `xxx-service`、版本号 `vX.Y.Z` 或动态事实词（发布/部署/上线/回滚/事故/故障/变更/当前/最近/历史/告警）→ `AGENTIC`。
2. 否则命中解释型词（是什么/什么是/解释/区别/原理/为什么）→ `DIRECT`。
3. 其余 → `RETRIEVAL`。

`DIRECT` 不再返回占位文本，而是调用模型给出通用技术解释，并显式声明未使用企业事实：`evidenceStatus=NO_ENTERPRISE_EVIDENCE`、`terminalReason=DIRECT_ANSWERED`、`citations=[]`。

## 当前实现（2026-09-05）

- Router 使用可审计规则输出 `DIRECT | RETRIEVAL | AGENTIC`、置信度和原因；当前不是模型分类器。
- AGENTIC 路径依次查询知识、历史事故、部署、最新服务状态和变更；五个工具全部只读。
- 循环最多 5 步，并保存调用签名防重复；动态事实工具没有明确 serviceName 时拒绝宽表扫描。首版的"15 秒 deadline"只是两次工具调用之间的检查，已被上文的完整预算模型取代（总预算 20 秒、单工具 6 秒、回答预留 8 秒）。
- 工具轨迹仅记录工具名、脱敏服务名、结果数量、耗时和状态；最终响应包含 step 和 terminal reason，不输出 chain-of-thought。
- 工具事实按 `T1...Tn` 编号，DeepSeek 每个事实结论必须引用工具编号；服务端拒绝无引用和未知引用，响应只返回实际引用的工具事实。这是引用存在性校验，不是逐句语义蕴含证明。
- V3 新增合成演示用事故、部署、状态与变更表。`demo-v1` 每类只含一条 payment-service 事实，不能冒充生产动态数据或性能测试。
