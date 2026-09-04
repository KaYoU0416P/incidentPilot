# 领域模型

## 模块边界

- `knowledge`：SourceDocument、ParentSection、Chunk、IngestionJob。
- `retrieval`：RetrievalQuery、Candidate、Evidence、RetrievalResult、Retriever。
- `diagnosis`：DiagnosisRequest、DiagnosisReport、Citation、QueryRoute。
- `agent`：AgentRun、AgentStep、ToolCall、ToolObservation、TerminalReason。
- `incident`：Incident、IncidentTimeline、RootCause、Resolution.
- `deployment`：Deployment、ChangeRecord、ArtifactVersion。
- `servicehealth`：ServiceSnapshot、Alert、MetricObservation。
- `evaluation`：EvalCase、GroundTruth、EvalRun、MetricResult。

## 核心值对象

标识符使用强类型值对象或语义明确的字符串字段。时间统一存 UTC，API 使用 ISO-8601。Evidence 持有稳定 sourceId、chunkId、原文片段和来源定位；Citation 只能引用已进入上下文且通过验证的 Evidence。

## DTO 规则

API DTO 不直接复用持久化 Entity。Tool 输入使用显式 schema 和 Bean Validation。外部适配器返回值先映射为领域对象，再进入 Agent 状态。

## 初始关系

一个 SourceDocument 包含多个 ParentSection，每个 ParentSection 包含多个 Chunk。Incident、Deployment、ChangeRecord、ServiceSnapshot 都关联 serviceName 和时间窗口。DiagnosisReport 聚合 Evidence/Citation，但不拥有源数据生命周期。

持久化层首批只建立 `document` 与 `document_chunk`，支撑可复现摄取和 Dense/Lexical Retrieval。`document_chunk.metadata` 使用 JSONB 承载可过滤但变化较快的来源属性；稳定关系和唯一性仍使用普通列、主键与外键。`embedding` 和 `search_vector` 是可重建索引数据，不成为领域对象的唯一事实源。

具体字段在首次实现对应模块前补齐，禁止为尚未进入 MVP 的能力提前创建表和 Entity。
