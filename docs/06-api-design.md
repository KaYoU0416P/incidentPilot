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
