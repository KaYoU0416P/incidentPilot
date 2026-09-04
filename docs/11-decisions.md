# 架构决策记录

## ADR-001: 使用模块化单体

Status: Accepted

Context: 一周内要交付完整检索、Agent、评测和工程能力，拆微服务会增加部署、数据一致性和观测成本。

Decision: 一个 Spring Boot 应用，按业务能力分包，以 port/adapter 隔离基础设施。

Reason: 保留清晰边界与可测试性，同时把时间投入核心学习目标。

Alternatives: 多模块 Maven；微服务。

Consequences: 部署简单；模块隔离依靠代码规则和测试，而非进程边界。

## ADR-002: 锁定 Spring AI 2.0.1 与 Spring Boot 4.1.1

Status: Accepted

Context: 项目要求 Spring AI 2.0.1 Stable，且禁止混用旧 API。官方 Getting Started 声明 2.0.x 支持 Spring Boot 4.0.x 和 4.1.x；当前 Boot 官方稳定版本包含 4.1.1，支持 Java 17～26。

Decision: Java 21、Spring Boot 4.1.1、Spring AI BOM 2.0.1；只使用 Maven Central，不配置 snapshot repository。

Reason: 使用当前稳定 patch，Java 21 兼容，并由 BOM 对齐 Spring AI 模块。

Alternatives: Boot 4.0.8（兼容且更保守）；Boot 3.x（不在 Spring AI 2.0.x 官方支持范围）。

Consequences: Boot 4 / Framework 7 生态较新；每个新 Spring AI API 必须先查 2.0.1 Reference/Javadoc。本机需从 JDK 17 补齐 JDK 21。

References:

- https://docs.spring.io/spring-ai/reference/getting-started.html
- https://docs.spring.io/spring-boot/system-requirements.html
- https://docs.spring.io/spring-ai/reference/upgrade-notes.html

## ADR-003: MySQL、Redis、Qdrant 分工

Status: Superseded by ADR-007

Context: 业务事实、短期状态和向量召回有不同一致性与查询需求。

Decision: MySQL 是权威业务库；Redis 只存短期状态/缓存/限流；Qdrant 存向量和可过滤 chunk payload。

Reason: 职责清晰，Qdrant 索引可重建，缓存故障不会改变事实。

Alternatives: 全部使用 PostgreSQL/pgvector；Redis 同时做向量库。

Consequences: 本地有三个依赖容器，运维面更大；通过 Compose 和健康检查控制复杂度。

## ADR-004: 先保留 Dense baseline，再引入 Hybrid/Rerank

Status: Accepted

Context: 没有 baseline 就无法证明优化有效。

Decision: Phase 1 固化 Dense 结果；Phase 2 逐步添加 BM25、RRF、rerank，每一步运行同一版本数据集。

Reason: 用可复现指标做技术选择。

Alternatives: 直接实现最终 Hybrid pipeline。

Consequences: 有少量重复实现，但获得可靠对照和更强可解释性。

## ADR-005: Qdrant 作为 Dense Vector Store

Status: Superseded by ADR-007

Context: 需要 HNSW 召回、payload filter 和明确的向量系统展示。

Decision: 使用 Qdrant；Spring AI 2.0.1 官方 starter 是 `spring-ai-starter-vector-store-qdrant`，配置前缀是 `spring.ai.vectorstore.qdrant`。

Reason: 官方集成明确且支持可移植 metadata filter；领域层仍依赖自有 Retriever port。

Alternatives: pgvector 简化组件；Elasticsearch/OpenSearch 统一 dense/sparse。

Consequences: 多一个服务；collection schema/embedding dimension 需显式版本化。Phase 1 首次编码 Qdrant API 前再次核对官方文档。

Reference: https://docs.spring.io/spring-ai/reference/api/vectordbs/qdrant.html

## ADR-006: 暂不实现 MCP、GraphRAG 与 Multi-Agent

Status: Accepted

Context: 它们不是证明 Retrieval/Agent/Evaluation 能力的前置条件。

Decision: MCP 仅在 Phase 1～3 和第 4 天门禁通过后考虑；GraphRAG、复杂 Multi-Agent 不进入一周 MVP。

Reason: 控制范围，避免简历关键词替代真实完成度。

Alternatives: 从第一天并行搭 MCP Server 或图数据库。

Consequences: 加分项可能延期，但核心链路更可能形成真实可演示闭环。

## ADR-007: Replace MySQL + Qdrant with PostgreSQL + pgvector

Status: Accepted

### Context

一周 MVP 的主要风险是 Retrieval、Hybrid、Rerank、Evaluation 和受控 Agent 能否形成真实闭环，而不是独立数据库组件数量。当前仓库尚无业务 Entity、Repository、向量数据或 Retrieval 实现，因此迁移窗口成本最低。

### Previous Design

MySQL 保存结构化业务事实，Qdrant 保存向量与 chunk payload，Redis 保存短期状态。这要求应用协调两个持久化事实/索引系统，并维护额外的连接、健康检查、备份与一致性边界。

### New Design

PostgreSQL 17 同时承载结构化业务数据、文档元数据、chunk、JSONB metadata、`tsvector` 全文检索与 pgvector embedding；Redis 继续只承载缓存、Agent 运行状态、限流和临时上下文。Dense 与 Lexical Retriever 仍通过项目自有 `Retriever` 契约暴露，上层融合与底层存储解耦。

### Why PostgreSQL

同一事务和查询引擎即可组合关系数据、时间/服务过滤、JSONB 与全文检索，减少 MVP 的跨系统一致性和运维成本，同时保留 Java 后端岗位相关的 SQL、索引和事务能力。

### Why pgvector

pgvector 支持精确/近似向量检索、cosine/L2/inner product、HNSW/IVFFlat，并可与 PostgreSQL metadata 过滤共同工作，足以支撑当前规模的 Dense baseline 与 Retrieval Evaluation。

### Why Remove Qdrant From MVP

Qdrant 不是过时或能力不足；当前 MVP 数据规模和目标不需要独立 Vector Database。PostgreSQL + pgvector 能降低基础设施复杂度，同时满足 Dense Retrieval、Metadata Filtering 和 Retrieval Evaluation。

### Why Not MongoDB

当前核心数据包含明确关系、约束、时间查询、全文检索与向量检索；MongoDB 不会减少组件数量，也不能替代本轮需要展示的 PostgreSQL SQL/索引能力，因此不加入 MVP。

### Trade-offs

- 优点：两项基础设施即可启动；文档、metadata、词法索引和向量处于同一数据一致性边界；本地验证更简单。
- 代价：PostgreSQL 同时承担 OLTP 与 Retrieval 负载；向量规模增长后需要重新评估查询隔离、索引维护、扩展能力和专用向量库。
- 约束：PostgreSQL 原生 FTS 统一称为 Lexical Retrieval，不声称是标准 BM25。

### Migration Impact

移除 MySQL driver/Flyway module、Qdrant 配置与健康代码、两个 Compose service；新增 PostgreSQL driver、Spring Boot 4 的 `spring-boot-starter-flyway`、PostgreSQL Flyway module、pgvector Compose service、PostgreSQL 健康探针和新的 Flyway schema。旧 MySQL/Qdrant 命名卷暂时保留，不在迁移中删除数据。

Embedding 维度由具体模型决定。初始 schema 使用无固定维度的 `vector` 列，先完成 extension、数据约束和 exact-search 能力；选定 baseline embedding model 后以独立 migration 固定维度并创建对应 HNSW 索引，避免把未经决定的 `1536` 写成永久事实。

### Future Option

若数据量、延迟、隔离或运维需求证明有必要，可新增 `QdrantDenseRetriever` 等 adapter，并用同一 Evaluation Dataset 与 PostgreSQL/pgvector 对照；上层 `Retriever`、Hybrid、RRF、Rerank 和 Agent 不随之重写。

References:

- https://github.com/pgvector/pgvector
- https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- https://documentation.red-gate.com/fd/postgresql-database-277579325.html
