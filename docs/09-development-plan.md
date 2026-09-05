# 七天开发计划与当前状态

最后更新：2026-09-05 18:05 CST

## 当前门禁

- 当前 Phase：Phase 4 Production Shape 已完成主要项，MVP 可交付
- 下一任务：项目集中学习（按 `agent.md` 的调用链顺序）；可选加分项见"明确延期"
- 阻塞：无。评测集仍是 6 份合成文档的 smoke dataset，任何质量或性能结论都不可外推
- 第 4 天门禁：已通过。Hybrid Retrieval + Evaluation 真实跑通，四路对照与逐题原始结果已落盘

## Phase 0 — Infrastructure

- [x] Repository bootstrap
- [x] Docker environment
- [x] PostgreSQL migration
- [x] pgvector extension
- [x] Redis verification
- [x] Spring datasource verification
- [x] 移除 MVP 的 MySQL/Qdrant 配置与运行容器（保留旧命名卷）

## Phase 1 — Dense Retrieval Baseline

- [x] 模型 HTTP 探针：DeepSeek `deepseek-v4-flash`；百炼 `text-embedding-v4` 1024 维
- [x] Document model
- [x] Chunk model
- [x] 幂等摄取服务与 PostgreSQL 回滚集成测试
- [x] `Retriever` 核心契约（用户手敲）
- [x] Spring AI 2.0.1 双供应商接线、自有模型接口、分批与响应校验、真实模型集成测试
- [x] Embedding pipeline
- [x] Store embedding（1024 维、HNSW、重复跳过与并发变化保护）
- [x] `PgVectorDenseRetriever` 核心流程（用户手敲后交由 Agent 继续完成）
- [x] TopK retrieval
- [x] Citation 编号存在性校验与证据不足降级
- [x] Minimal RAG answer 与本地 HTTP 演示
- [x] 保存 baseline 配置和逐题原始结果

## Phase 2 — Retrieval Engineering

- [x] PostgreSQL Lexical Retrieval 核心流程
- [x] GIN index
- [x] `HybridRetriever`
- [x] RRF（k=60，独立单测）
- [x] Lightweight Reranking Pipeline 与真实对照（无指标收益，不设为默认；非 cross-encoder）
- [ ] Metadata filter — 明确延期，见下方"明确延期"
- [ ] Parent-child chunk — 明确延期
- [x] `demo-v1` Evaluation dataset（6 合成文档、12 可回答、2 无答案）
- [x] Recall@K、MRR、nDCG 计算与单测
- [x] 比较 Dense / Lexical / Hybrid+RRF / Hybrid+RRF+轻量 Rerank

## Phase 3 — Agentic RAG

- [x] Direct/Retrieval/Agentic Router（确定性规则，企业事实信号优先于解释型措辞）
- [x] DIRECT 路由返回真实通用解释，`evidenceStatus=NO_ENTERPRISE_EVIDENCE`
- [x] 五个只读 Tool，动态事实工具强制 serviceName 与显式时间窗口
- [x] `ToolStatus` 区分 SUCCESS/EMPTY/SKIPPED/TIMEOUT/FAILED，非 SUCCESS 不产生证据
- [x] 有界 Agent Loop：5 steps、20s 总预算、6s 单工具超时、8s 回答预留、6000 字符上下文预算
- [x] 完整 deadline 传播、单工具取消与 `loopTermination` / `terminalReason`
- [x] Retrieval/Agent 引用编号存在性校验与无效引用降级
- [x] 显式拒答优先于引用校验（ADR-011）
- [x] 工具轨迹与 Agentic Evaluation（行为评测 `behavior-agent-1/2`）

## Phase 4 — Production Shape

- [x] 生命周期 SSE，emitter 超时绑定后台任务取消并归还并发许可（真实验证）
- [x] 模型 timeout/retry
- [x] Redis 限流（固定窗口 20/60s）与并发控制（max 4），真实 HTTP 验证
- [x] Redis Agent 运行状态（TTL 1h，不持久化回答正文）与 `GET /api/v1/agent/runs/{runId}`
- [x] 业务 metrics：`incidentpilot.rag.diagnosis` / `.agent.run` / `.agent.tool` / `.sse.terminated` / `.request.rejected` / `.request.guard_degraded`
- [x] requestId 关联（MDC + 响应头 + 错误体）与审计日志
- [x] Prompt/tool injection 防护验证（行为评测两个注入 case）
- [x] 无答案与对抗性问题的实际行为验收
- [x] 一键启动、演示脚本、回归指标和面试材料
- [ ] tracing（分布式链路）— 明确延期

## 明确延期（不是"已完成"，也不是"静默放弃"）

| 项 | 状态 | 原因 |
| --- | --- | --- |
| Metadata filter | 延期 | Schema 与 JSONB 索引已就绪，但 `demo-v1` 只有 6 份文档，加过滤条件无法产生可测量差异，做了也无法验证收益 |
| Parent-child chunk | 延期 | 当前每份文档只有 1 个 chunk，父子结构没有作用对象；需要先扩充语料 |
| 模型原生 Tool Calling | 延期 | 当前是确定性编排。要改成模型自主规划，必须先验证 DeepSeek 在 Spring AI 2.0.1 下的 function calling 行为并重做 Agent 评测，本轮不做，也不在任何材料里包装成已实现 |
| Cross-encoder Rerank | 延期 | 需要先核实可调用 API 与成本；现有 lightweight rerank v1 是规则重排，不改名冒充 |
| 分布式 tracing | 延期 | 单体单实例，requestId 关联已足够定位；引入 tracing 后端超出 MVP |
| MCP | 延期 | ADR-006 的门禁条件是核心稳定，本轮核心刚稳定，不在交付范围 |

## Phase 0 迁移完成定义

Compose 仅包含 PostgreSQL/pgvector 与 Redis；`vector` extension 已启用；Flyway schema 已执行；应用在 Java 21 启动并连接 PostgreSQL/Redis；Actuator 和依赖健康 API 为 `UP`；MySQL/Qdrant 只允许存在于历史 ADR/开发日志或未来可选方案说明中。
