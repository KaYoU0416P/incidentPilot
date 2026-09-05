# API 设计

## Phase 0

- `GET /actuator/health`：应用健康。
- `GET /api/v1/system/dependencies`：依赖健康汇总（计划在 Phase 0 完成）。

## 目标 API

- `POST /api/v1/diagnoses`：非流式诊断。
- `POST /api/v1/diagnoses/stream`：SSE 诊断。
- `POST /api/v1/knowledge/documents`：受控摄取。
- `POST /api/v1/evaluations/runs`：启动离线评测。
- `GET /api/v1/evaluations/runs/{id}`：读取真实指标。

## 响应约定

错误响应含 `code`、`message`、`requestId`、`timestamp`，不泄漏 secret、prompt 或堆栈。诊断响应含 route、answer、citations、evidenceStatus、latency 和 token usage（供应商可提供时）。

## SSE 事件

计划事件：`route`、`retrieval`、`tool_call`、`tool_result`、`answer_delta`、`citation`、`completed`、`error`。事件只暴露可审计动作摘要，不输出模型 chain-of-thought。每个事件含 runId、sequence、timestamp。

具体 JSON Schema 在 Controller 实现前补齐并用契约测试锁定。

当前首版 `POST /api/v1/diagnoses/stream` 已实现 `started`、`completed`、`error`，使用 Java 21 virtual thread 执行阻塞模型调用，超时 35 秒。它提供生命周期流式事件，尚未提供逐 token `answer_delta`，不能宣传为 token streaming。真实事件样例保存于 `artifacts/demo-sse.txt`。

## 交付版 API（2026-09-05 加固后）

所有模型端点仅在 `models` profile 启用，默认绑定 `127.0.0.1`。每个响应都带 `X-Request-Id`；客户端可传入该头，但只接受 UUID 形状，否则服务端重新生成。

| 方法 | 路径 | 说明 | 限流/并发 |
| --- | --- | --- | --- |
| GET | `/actuator/health` | 应用与依赖健康 | 否 |
| GET | `/actuator/metrics/{name}` | 业务与框架指标 | 否 |
| GET | `/api/v1/system/dependencies` | PostgreSQL/pgvector 与 Redis 探活 | 否 |
| POST | `/api/v1/knowledge/documents` | 受控摄取 + 向量索引 | 否 |
| POST | `/api/v1/retrieval/search` | 纯检索，不调用模型 | 否 |
| POST | `/api/v1/diagnoses` | 单轮 RAG 诊断 | 是 |
| POST | `/api/v1/diagnoses/stream` | 生命周期 SSE 诊断 | 是 |
| POST | `/api/v1/agent/diagnoses` | 有界 Agent 诊断 | 是 |
| GET | `/api/v1/agent/runs/{runId}` | 读取 Redis 中带 TTL 的运行状态 | 否 |
| POST | `/api/v1/demo/seed` | 幂等导入合成演示语料 | 否 |
| GET | `/api/v1/demo/cases` | 演示评测问题 | 否 |
| POST | `/api/v1/evaluations/runs` | 四路检索对照评测（不调用模型） | 否 |
| GET | `/api/v1/evaluations/runs/{id}` | 读取检索评测原始结果 | 否 |
| POST | `/api/v1/evaluations/runs/behavior` | 行为评测：拒答、注入、Agent 路由与引用（调用模型） | 否 |
| GET | `/api/v1/evaluations/runs/behavior/{id}` | 读取行为评测结果 | 否 |

### 错误码

| HTTP | code | 触发条件 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Bean Validation、非法 JSON、非法参数 |
| 429 | `RATE_LIMITED` | 固定窗口内模型请求超过 `rate-permits` |
| 429 | `CONCURRENCY_LIMITED` | 在途模型请求超过 `max-concurrent` |
| 503 | `DEPENDENCY_OR_PROCESSING_FAILURE` | 依赖或处理失败；堆栈只进服务端日志 |

错误体固定为 `{code, message, requestId, timestamp}`，不回显 prompt、secret 或堆栈。

### Agent 诊断响应

`{runId, route{route,confidence,reason}, answer, traces[], citations[], evidenceStatus, steps, loopTermination, terminalReason, latencyMs}`

- `traces[]`：`{tool, serviceName, status, returnedFacts, acceptedFacts, latencyMs, note}`，`status ∈ SUCCESS|EMPTY|SKIPPED_MISSING_PARAMETER|TIMEOUT|FAILED`。
- `citations[]`：`{id, tool, fact, source, observedAt}`，只包含答案真实引用的工具事实。
- `loopTermination ∈ TOOLS_COMPLETED|MAX_STEPS|DEADLINE_EXCEEDED|CONTEXT_BUDGET_EXHAUSTED`。
- `terminalReason ∈ ANSWERED|DIRECT_ANSWERED|NO_EVIDENCE|INVALID_REFERENCES|MODEL_REFUSED|DEADLINE_EXCEEDED|ANSWER_TIMEOUT|ANSWER_FAILED`。
- `evidenceStatus ∈ REFERENCES_VALIDATED|NO_ENTERPRISE_EVIDENCE|INSUFFICIENT|UNAVAILABLE`。

`REFERENCES_VALIDATED` 只表示答案里的编号都存在于本次上下文，不表示语义正确。答案以“证据不足”等拒答措辞开头时一律降级为 `INSUFFICIENT`，即使其中出现了合法编号。

### SSE 事件结构

```text
event: started
id: 1
data: {"requestId":"<uuid>","sequence":1,"payload":{"mode":"hybrid","topK":3}}

event: completed
id: 2
data: {"requestId":"<uuid>","sequence":2,"payload":{ ...与 POST /api/v1/diagnoses 相同的 Diagnosis... }}
```

错误路径为 `event: error`，`data` 为 `{requestId, message}`，不含堆栈。emitter 超时由 `incidentpilot.reliability.sse-timeout`（默认 35s）控制，超时会取消后台任务并归还并发许可。这是生命周期事件流，不是逐 token streaming。

## 首版交付 API（2026-09-05）

所有模型端点仅在 models profile 启用，本地默认绑定 127.0.0.1。
- POST /api/v1/knowledge/documents：接收 sourceKey/title/documentType/serviceName/sourceUri/chunks[{chunkIndex,content,metadata}]，执行摄取后向量索引，返回 documentId 与索引状态。外部向量失败时正文已持久化，重复相同请求可重试索引。
- POST /api/v1/retrieval/search：{query,topK,mode}，mode 为 dense/lexical/hybrid，返回有序 RetrievalResult。
- POST /api/v1/diagnoses：{query,topK,mode}，返回 answer、citations、evidenceStatus、retriever、latencyMs。未提供 mode 时使用 dense baseline。
- POST /api/v1/demo/seed：导入有明确 demo-v1 命名空间的合成演示知识；幂等重复导入。
