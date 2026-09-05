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


## ADR-008: Separate Chat and Embedding providers

Status: Accepted

Context: 用户选用 DeepSeek 官方 API；Dense baseline 还需要独立 Embedding 服务。

Decision: Chat 使用 DeepSeek `deepseek-v4-flash`；Embedding 使用阿里云百炼北京地域 `text-embedding-v4`，显式请求 1024 维。两家服务独立配置密钥与地址，框架接线由 Agent 完成，Retriever 核心仍由用户手敲。

Trade-offs: 增加一份供应商配置，但避免本周部署本地推理服务。当前没有检索质量对照结果，不宣称该组合效果最优。文档与查询必须使用相同 Embedding 模型和维度；更换 Embedding 模型需要重建向量。模型别名可能更新，baseline 应记录调用日期与响应模型字段。

Verification: 两个官方 HTTP 请求成功。DeepSeek 返回非空回答；百炼一次批量返回两条 1024 维、有限且非零的向量。百炼本次使用官方公共北京兼容地址 `https://dashscope.aliyuncs.com/compatible-mode/v1`；官方推荐后续采用业务空间专属域名，需获取真实 WorkspaceId 后配置。后续更新（2026-09-05）：Spring AI 2.0.1 接线及真实模型集成测试已通过；持久化、HNSW 与完整 RAG 仍未实现。

References:

- https://api-docs.deepseek.com/zh-cn/
- https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api
- https://help.aliyun.com/en/model-studio/embedding-interfaces-compatible-with-openai

## ADR-009: Agent 预算建模为一等对象并强制传播

Status: Accepted

Context: 首版 Agent 只在两次工具调用之间检查 deadline，单次工具调用和最终回答可以任意超时；上下文只是无上限累加的 StringBuilder；工具缺少参数时返回的提示文本还会被当成有效事实交给模型。这些都是"看起来有约束、实际没有约束"。

Decision: 引入 `AgentBudget`（max-steps / total-budget / tool-timeout / answer-reserve / context-chars / fact-chars / fact-lookback），每个工具与最终回答都在虚拟线程 executor 上执行并受 `Future.get(timeout)` 约束，超时 `cancel(true)`；工具结果引入 `ToolStatus`，只有 SUCCESS 的 `ToolFact` 能编号成证据。

Reason: 预算必须能被测试证明。现在每一项都有对应断言：步进 Clock 证明预算耗尽会终止循环，慢工具证明单工具超时会被取消，超长事实证明上下文预算会截断，缺参数工具证明不会触库也不会变成证据。

Alternatives: 只给外层加一个总超时（无法定位是哪一步超时，也无法在超时后保留部分轨迹）；用 Spring `@Async` + `@Transactional` 超时（覆盖不到模型 HTTP 调用）。

Consequences: 构造函数参数变多、需要显式注入 executor 与 Clock；换来的是可审计的 `loopTermination` 与 `terminalReason`。预算是墙钟约束，工具线程被中断后底层驱动是否立即返回不由本项目保证，这一边界写入 `08-security-and-reliability.md`。

## ADR-010: Redis 承担运行状态、限流与并发控制，故障时 fail-open

Status: Accepted

Context: 项目已有 Redis，但此前只做健康探活。需要可演示的可靠性控制，同时不能引入复杂分布式架构。

Decision: Redis 承担三件事——`incidentpilot:agent:run:{runId}` 带 TTL 的运行状态（不持久化模型回答正文，只存元数据与工具轨迹）、按客户端 IP 的固定窗口限流、全局在途请求计数。三者都在 Redis 不可用时 fail-open，并记录 `incidentpilot.request.guard_degraded` 指标。

Reason: 固定窗口与计数器实现简单、可用 mock 完整单测、可用真实 HTTP 并发验证，符合"选择简单、可测试的方案"的约束。运行状态不存回答正文，避免在缓存里复制模型输出。

Alternatives: 令牌桶/滑动窗口（更平滑，但需要 Lua 脚本，测试成本高于本轮收益）；进程内 Semaphore（无法跨实例，也无法在演示中体现 Redis 的作用）；fail-closed（更安全，但本地演示时 Redis 抖动会直接让整个诊断链路不可用）。

Consequences: 固定窗口在窗口边界可能出现两倍瞬时速率；fail-open 意味着 Redis 故障期间配额失效。两项都已登记，面向公网部署必须改为 fail-closed 并换成滑动窗口。

## ADR-011: 显式拒答优先于引用编号存在性校验

Status: Accepted

Context: 行为评测第一次运行时，语料外问题的回答是"证据不足。提供的证据 [E1][E2][E3] 均为故障排查手册内容，未包含相关事件"。引用编号都合法存在，于是被判为 `REFERENCES_VALIDATED`——一次真实的误判。

Decision: 在引用校验之前先匹配拒答措辞（`证据不足` / `无法回答` / `没有足够证据` 开头），命中即返回 `INSUFFICIENT` 并清空引用；Agent 侧同样处理，`terminalReason=MODEL_REFUSED`。

Reason: 引用存在性只能证明编号来自本次上下文，不能证明模型在断言而不是在拒答。诊断场景下把拒答误报成"已校验引用"，比漏掉一个有效答案危险得多。

Alternatives: 要求模型输出结构化 `{status, answer, citations}`（更准确，但需要为两个供应商分别验证 structured output 支持，超出本轮范围，列为后续项）；用 LLM-as-judge 判断是否拒答（引入 judge 偏差且成本翻倍）。

Consequences: 模型若一边以"证据不足"开头、一边给出有效结论，也会被保守降级为证据不足。这是有意的保守取舍，已写入 `07-evaluation.md`。后续若改为结构化输出，本 ADR 可被取代。
