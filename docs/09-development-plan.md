# 七天开发计划与当前状态

最后更新：2026-09-04 12:36 CST

## 当前门禁

- 当前 Phase：Phase 1 Dense Retrieval Baseline
- 下一任务：确定 baseline EmbeddingModel；Agent 接入模型并增加定维/HNSW migration，随后由用户手敲 `PgVectorDenseRetriever` 核心查询流程
- 阻塞：无；Embedding/Chat 模型 provider 需在 HNSW migration 和模型集成前确定
- 第 4 天门禁：Hybrid Retrieval + Evaluation 必须真实跑通，否则砍掉 MCP、复杂 Agent、GraphRAG

## Phase 0 — Infrastructure

- [x] Repository bootstrap
- [x] Docker environment
- [x] PostgreSQL migration
- [x] pgvector extension
- [x] Redis verification
- [x] Spring datasource verification
- [x] 移除 MVP 的 MySQL/Qdrant 配置与运行容器（保留旧命名卷）

## Phase 1 — Dense Retrieval Baseline

- [x] Document model
- [x] Chunk model
- [x] 幂等摄取服务与 PostgreSQL 回滚集成测试
- [x] `Retriever` 核心契约（用户手敲，8 tests 全量通过）
- [ ] Embedding pipeline
- [ ] Store embedding
- [ ] `PgVectorDenseRetriever` 核心流程（用户手敲）
- [ ] TopK retrieval
- [ ] Citation
- [ ] Minimal RAG answer
- [ ] 保存 baseline 配置和原始结果

## Phase 2 — Retrieval Engineering

- [ ] PostgreSQL Lexical Retrieval 核心流程（用户手敲）
- [ ] GIN index
- [ ] `HybridRetriever`（用户手敲）
- [ ] RRF（用户手敲）
- [ ] Reranker / Reranking Pipeline（用户手敲核心编排）
- [ ] Metadata filter
- [ ] Parent-child chunk
- [ ] Evaluation dataset
- [ ] Recall@K、MRR、nDCG（用户手敲指标核心）
- [ ] 比较 Dense / Lexical / Hybrid+RRF / Hybrid+RRF+Rerank

## Phase 3 — Agentic RAG

- [ ] Direct/Retrieval/Agentic Router（用户手敲核心判断）
- [ ] 五个只读 Tool
- [ ] 有界 Agent Loop（用户手敲状态推进与终止）
- [ ] Evidence Verification
- [ ] 工具轨迹与 Agentic Evaluation

## Phase 4 — Production Shape

- [ ] SSE、timeout/retry、rate limit、Redis 状态
- [ ] metrics/tracing/token/latency/tool audit
- [ ] Prompt/tool injection 防护验证
- [ ] 一键启动、演示脚本、回归指标和面试材料

## Phase 0 迁移完成定义

Compose 仅包含 PostgreSQL/pgvector 与 Redis；`vector` extension 已启用；Flyway schema 已执行；应用在 Java 21 启动并连接 PostgreSQL/Redis；Actuator 和依赖健康 API 为 `UP`；MySQL/Qdrant 只允许存在于历史 ADR/开发日志或未来可选方案说明中。
