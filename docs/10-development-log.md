# 开发日志

本文件只追加，不改写历史记录。

## 2026-09-04 00:30 CST

### Task

从空工作区启动 IncidentPilot，先建立文档事实源并核验核心版本。

### Changes

- 确认工作区无文件且不是 Git repository。
- 创建 13 份规定文档并给出模块化单体目录设计。
- 核验 Spring AI 2.0.1 Stable 官方文档、Spring Boot 兼容范围、Qdrant starter 和配置名称。
- 选择 Spring Boot 4.1.1、Java 21、Spring AI BOM 2.0.1。

### Result

文档设计已先于代码建立。Spring AI 2.0.x 官方支持 Boot 4.0.x/4.1.x；Boot 4.1.1 支持 Java 17～26。

### Problems

本机默认 Java 为 17.0.19，尚未满足项目 Java 21 编译目标。Git 尚未初始化。

### Decision

采用模块化单体与框架无关核心 port；MVP 不引入微服务。Spring AI 2.0.1 通过 BOM 固定。模型 provider 在 Phase 1 开始前再确定，Phase 0 不要求 API Key。

### Next

准备 JDK 21，初始化 Git/Maven 工程，加入 Boot 基础依赖与三项基础设施 Compose，然后逐项编译、测试、启动和探活。

## 2026-09-04 01:00 CST

### Task

初始化 Phase 0 工程，建立仓库级 Agent 日常规范，并继续完成基础设施闭环。

### Changes

- 仓库目标路径调整为 `/Users/kayou/Documents/incidentPilot`。
- 参考本地项目规范创建 `agent.md` 与标准 `AGENTS.md` 入口。
- 按用户要求将亲自手敲范围收窄为 Retriever 核心契约、RRF、Agent Loop 状态推进和关键评测指标。
- 安装 JDK 21.0.12.1，初始化 Git 和 Spring Boot 4.1.1 Maven 工程，导入 Spring AI BOM 2.0.1。
- 添加 MySQL、Redis、Qdrant Compose 与依赖健康 API，并将健康检查拆为独立 Probe。

### Result

工程已在 Java 21 下完成干净编译和 Spring Boot 上下文加载；日常协作、验证和交接规则已落盘。

### Verification

- `mvn clean test`：通过，2 tests，0 failures，0 errors。
- Maven 实际解析 `spring-ai-bom:2.0.1` 与 `spring-boot:4.1.1`。
- `docker compose config --quiet`：通过。

### Problems

Docker Engine 访问 Docker Hub 与 GHCR 均返回 `Bad Gateway`，尚未完成 Qdrant 拉取和三依赖真实探活。

### Decision

机械与集成代码由 Agent 主动完成；只把最关键的算法与生命周期代码保留给用户手敲。Docker Registry 问题先作为 Phase 0 阻塞处理，不用 mock 伪造成功。

### Next

修复 Docker Registry 访问，启动三项依赖和应用，真实请求 Actuator 与依赖健康 API。

## 2026-09-04 01:18 CST

### Task

完成 Phase 0 真实运行闭环，并把下一步收敛到用户需要手敲的最小 Retrieval 核心。

### Changes

- 诊断出 OrbStack Docker Engine 通过内置代理访问 Docker Hub 返回 `Bad Gateway`；未重启或修改全局代理。
- 安装 `crane 0.22.0`，由宿主机下载固定版本的 MySQL、Redis、Qdrant linux/arm64 镜像后导入 Docker。
- 按用户要求停止既有 `flash-coupon-rabbit` 容器；未删除容器或数据。
- 启动 IncidentPilot 三项依赖及命名卷。
- 发现 Homebrew MySQL/Redis 已占用 `3306/6379`，将项目默认宿主端口隔离为 `13306/16379`，容器内部标准端口不变。
- 更新 Compose、应用配置、`.env.example`、README、数据设计与 Retrieval 契约设计。

### Result

Phase 0 完成。应用在 Java 21 下连接 MySQL 8.4.11、Redis 8.2.9 和 Qdrant 1.18.2；Actuator 与自定义依赖健康接口均返回 `UP`。当前进入 Phase 1 Dense RAG Baseline。

### Verification

- `mvn -o -Dmaven.repo.local=/tmp/incidentpilot-m2 test`：通过，2 tests，0 failures，0 errors。
- `docker compose ps`：MySQL、Redis、Qdrant 全部 `healthy`。
- `GET /actuator/health`：整体 `UP`，db、redis、qdrant 均 `UP`。
- `GET /api/v1/system/dependencies`：MySQL `SELECT 1`、Redis `PONG`、Qdrant `/healthz` 均 `UP`。
- 应用实际连接 MySQL `127.0.0.1:13306`，服务端版本 `8.4.11`。

### Problems

OrbStack Docker Engine 的 Registry 代理仍返回 `Bad Gateway`；本轮采用可复现的宿主机下载再导入方案，不影响现有本地镜像启动。Codex 沙箱内 Maven 启动插件的首次联网解析受 SOCKS 代理冲突影响，因此应用验证使用 Maven 测试报告中的已解析 classpath 直接启动主类。

### Decision

本项目默认宿主端口避开常驻 Homebrew 服务；不为了修复项目而停止用户的 MySQL/Redis。Phase 1 公共 Retrieval 契约不暴露 Spring AI/Qdrant 类型，也不提前接受任意 metadata `Map`。

### Next

用户依次手敲 `RetrievalQuery`、`RetrievedChunk`、`RetrievalResult` 和 `Retriever` 四个最小核心文件；Agent 随后实现校验测试、演示语料、摄取与 Dense Qdrant 适配器。

## 2026-09-04 10:24 CST

### Task

把已初始化仓库从 MySQL + Qdrant 有控制地迁移为 PostgreSQL + pgvector + Redis，并恢复 Code、Architecture、Docs 一致。

### Changes

- 先审计 Git、构建、配置、Java 包、测试、文档与运行容器；确认尚无 Entity、Repository、Retrieval 实现或业务数据依赖。
- 新增 ADR-007，明确取代 ADR-003/ADR-005；保留旧 ADR 与开发日志作为真实历史。
- 全面更新项目总纲、架构、领域、RAG、数据、Evaluation、安全、计划、面试笔记、Agent 规范和 README。
- Compose 收敛为 `pgvector/pgvector:0.8.6-pg17-trixie` 与 `redis:8.2.9-bookworm`；移除 MySQL/Qdrant 容器但保留旧命名卷。
- Maven 移除 MySQL driver/Flyway module，加入 PostgreSQL driver、`spring-boot-starter-flyway` 与 PostgreSQL Flyway module。
- 删除 Qdrant 配置/HTTP Client/健康代码，新增 PostgreSQL + pgvector 健康探针。
- 新增 Flyway V1：启用 `vector` extension，创建 `document`、`document_chunk`、JSONB、generated `tsvector` 与 GIN/B-tree 索引。

### Result

Phase 0 架构迁移完成，当前进入 Phase 1 Dense Retrieval Baseline。应用无需模型 API Key 即可连接 PostgreSQL/pgvector 与 Redis 并启动。

### Verification

- 官方核验：pgvector 当前固定镜像 `0.8.6-pg17-trixie`；Spring AI 2.0.1 PGvector starter 为 `spring-ai-starter-vector-store-pgvector`；Spring Boot 4.1 使用 `spring-boot-starter-flyway`。
- `docker compose config --quiet`：通过。
- `docker compose ps`：PostgreSQL/pgvector、Redis 均 `healthy`。
- `mvn -o -Dmaven.repo.local=/tmp/incidentpilot-m2 test`：通过，2 tests，0 failures，0 errors。
- Flyway：V1 成功执行；`flyway_schema_history`、`document`、`document_chunk` 存在。
- PostgreSQL：17.11；`vector` extension：0.8.6；metadata/search_vector GIN 索引存在。
- 事务回滚探针：cosine distance `0`、Lexical match `true`、JSONB containment `true`，测试数据未保留。
- `GET /actuator/health`：整体、db、pgvector、redis 均 `UP`。
- `GET /api/v1/system/dependencies`：PostgreSQL/vector 与 Redis 均 `UP`。

### Problems

OrbStack Docker Engine 访问 Registry 的代理仍返回 `Bad Gateway`；沿用宿主机 `crane` 下载并导入固定镜像。首次启动发现 Spring Boot 4 仅加入 `flyway-core` 不会启用迁移自动配置，已依据官方文档改用 `spring-boot-starter-flyway` 并重新验证。

### Decision

- PostgreSQL 统一承载结构化数据、JSONB、FTS 和 pgvector；Redis 只承载短期状态。
- PostgreSQL FTS 称为 Lexical Retrieval，不声称是 BM25。
- 未确定 EmbeddingModel 前不猜维度、不建立 HNSW；初始 `vector` 列支持 exact search，模型确定后以独立 migration 固定维度和 HNSW。
- 暂不启用 Spring AI PGvector starter，避免尚无 EmbeddingModel 时破坏基础启动；核心 Retriever 仍保持框架无关。

### Next

用户先手敲 `RetrievalQuery`，再依次完成最小 `RetrievedChunk`、`RetrievalResult`、`Retriever` 契约；Agent 负责测试、数据访问和配置脚手架。随后由用户手敲 `PgVectorDenseRetriever` 的核心查询流程。

## 2026-09-04 12:23 CST

### Task

以 Pair Programming 完成框架无关的最小 Retrieval 核心契约，并建立可执行的边界测试。

### Changes

- 用户手敲 `RetrievalQuery`、`RetrievedChunk`、`RetrievalResult` 与 `Retriever`。
- 修正 `RetrievedChunk` 文件名和 record 名中的拼写错误。
- Agent 新增 `RetrievalContractTest`，覆盖查询清理与校验、chunk 字段约束、有限分数、结果快照不可变性和 `Retriever` Lambda 契约。

### Result

Phase 1 的最小 Retrieval port 与数据模型完成。领域契约不暴露 Spring AI、PostgreSQL 或 pgvector 类型，可被 Dense、Lexical 和 Hybrid adapter 共同实现。

### Verification

- `mvn -o -Dmaven.repo.local=/tmp/incidentpilot-m2 test`：通过，8 tests，0 failures，0 errors。
- 其中 `RetrievalContractTest`：6 tests 全部通过。

### Problems

首次输入 `RetrievedChunk` 时文件名和 record 名遗漏字母 `t`；在继续前通过仓库检查发现并由用户修正。无遗留阻塞。

### Decision

- `RetrievalResult` 保存检索器名称和有序不可变结果快照，支持后续评测与审计。
- `RetrievedChunk.score` 只要求为有限数，不强制在 `0～1`，避免错误统一不同检索器的分数空间；跨检索器融合由后续 RRF 处理。

### Next

Agent 实现 Document/Chunk Entity、Repository 与摄取脚手架；确定 EmbeddingModel 后固定 vector 维度并创建 HNSW migration，再由用户手敲 `PgVectorDenseRetriever` 核心查询流程。

## 2026-09-04 12:36 CST

### Task

实现 Document/Chunk 持久化与幂等摄取脚手架，为 Dense Retrieval 准备可追踪、可重复写入的知识数据。

### Changes

- 新增 `DocumentEntity`、`DocumentChunkEntity` 与两个 Spring Data JPA Repository；JSONB metadata 使用 Hibernate JSON 映射，数据库生成的 `search_vector` 和 pgvector 字段不泄漏到摄取领域模型。
- 新增 `DocumentIngestionRequest`、`ChunkInput`、结果状态、SHA-256 计算器与事务型 `DocumentIngestionService`。
- 同一 `sourceKey + contentHash` 不重写 chunk；正文变化时保留 document id 并整体替换 chunk。
- 新增可注入 UTC `Clock`；应用生成 UUID 的 Entity 实现 `Persistable.isNew()`，避免批量新增时出现无意义的存在性查询。
- 关闭未使用的 Redis Repository 扫描，Redis 连接与健康检查保持不变。
- 新增 4 个摄取单元测试和 1 个可显式启用的 PostgreSQL 集成测试；README 登记运行命令。

### Result

Document/Chunk model 与不含 Embedding 外部调用的摄取事务已完成。知识正文、metadata 和 PostgreSQL generated `search_vector` 能真实落库；Embedding 仍按设计留给下一步在事务外计算。

### Verification

- 普通 `mvn test`：13 tests，0 failures，0 errors，1 个需显式启用的 PostgreSQL 测试 skipped。
- `RUN_POSTGRES_TESTS=true ... -Dtest=DocumentIngestionPostgresIntegrationTest test`：1 test 通过，真实验证 CREATED → CONTENT_UNCHANGED → CONTENT_REPLACED。
- PostgreSQL 17.11 schema validation、JSONB 写入、generated `tsvector` 和事务替换路径均通过。
- 测试完成后查询 `source_key like 'integration-test:%'`：0 rows，确认事务回滚无残留。

### Problems

首次真实启动时 Hibernate 将 Java `String` 默认按 VARCHAR 校验，而 PostgreSQL `content_hash` 是 `CHAR(64)`/`bpchar`，导致 schema validation 失败。保留数据库设计并为字段显式声明 Hibernate `SqlTypes.CHAR` 后复测通过；未通过关闭 validation 绕过。

### Decision

- 摄取服务只接收已解析、已切分的 chunk，不读取文件、不决定 chunk 策略，也不在数据库事务里调用 Embedding API。
- 内容哈希按已排序的 `chunkIndex + UTF-8 字节长度 + 正文` 计算，防止简单拼接边界歧义。
- 普通测试不强依赖 Docker；真实 PostgreSQL 测试通过 `RUN_POSTGRES_TESTS=true` 显式开启。

### Next

确定 baseline EmbeddingModel 与维度；Agent 接入模型、实现批量 embedding 写回和 HNSW migration，之后由用户手敲 `PgVectorDenseRetriever` 核心查询流程。


## 2026-09-05 12:19 CST

### Task
验证用户本地配置的 DeepSeek 与百炼 API，为 Phase 1 模型接入确定依据。

### Changes
创建被 Git 忽略、权限为 600 的本地 `.env`，由用户自行填写密钥；更新模型决策和开发计划。未修改 Java 核心代码。

### Result
两家官方 HTTP API 探针成功；确定 Chat 为 `deepseek-v4-flash`，Embedding 为 `text-embedding-v4`、1024 维。

### Verification
DeepSeek 简短请求返回非空回答。百炼两条合成中文测试文本批量返回两条 1024 维向量，所有数值有限且向量非零。工具仅输出状态和维度，未回显密钥或完整响应。此次未运行 Maven、应用或数据库测试。

### Problems
无 HTTP 探针阻塞。Spring AI 适配与数据库写回尚未实现；不能将本次探针视为完整 RAG 验收。

### Decision
采用 ADR-008；固定当前 baseline 参数，后续效果判断使用真实评测。

### Next
核对 Spring AI 2.0.1 官方 API，完成模型接线与测试，随后实现批量向量写回及独立定维/HNSW migration，再进行 Dense Retriever 核心 Pair Programming。


## 2026-09-05 12:29 CST

### Task
接入 DeepSeek Chat 与阿里云百炼 Embedding，遵守 Spring AI 2.0.1 和核心手敲边界。

### Changes
- 先更新 RAG 设计，新增 BOM 管理的 `spring-ai-openai` 依赖与显式模型配置。
- 新增 `models` profile，从未跟踪的 `.env` 或环境读取两家独立配置；默认基础设施启动无需模型密钥。
- 新增自有 `TextEmbedder` / `AnswerGenerator` 接口与 Spring AI 适配器；Embedding 分批最多 10 条、按 index 对齐并校验数量、维度、有限性和非零。
- Chat 固定 baseline 关闭 thinking，最大输出 1024 tokens；两家请求超时 30 秒，自动重试 0 次。
- 新增本地 HTTP 合约测试、向量响应边界测试和显式开启的真实模型测试；更新 README、计划、ADR 和面试笔记。

### Result
模型接入完成，可以从 Spring 应用内调用两家供应商。未改动用户手敲的 Retriever 契约或实现 Dense Retriever；向量数据库写回和完整 RAG 仍待下一步。

### Verification
- Java 21 + Maven：编译通过，依赖为 Spring AI 2.0.1。
- 普通 `mvn test`：21 tests，0 failures，0 errors，2 skipped（真实模型与 PostgreSQL 测试默认跳过）。
- `RUN_MODEL_TESTS=true RUN_POSTGRES_TESTS=true ... -Dtest=ModelProviderIntegrationTest,DocumentIngestionPostgresIntegrationTest test`：2 tests 全部通过，无跳过；模型真实返回非空回答和两条有效的 1024 维向量。
- 本地 HTTP 合约验证两家不同 Authorization、准确 API 路径与模型参数、11 条输入分为 10+1、乱序向量 index 对齐、503 只请求一次。
- 在本轮临时端口 18087 使用 `models` 启动实际应用；`GET /actuator/health` 为 UP，`GET /api/v1/system/dependencies` 的 PostgreSQL/vector 与 Redis 均 UP。
- `git diff --check` 通过；扫描源码、文档、测试报告和本轮运行日志，未发现本地真实密钥值。
- 临时应用已关闭；保留本轮之前存在的 PostgreSQL 与 Redis 容器。未 commit 或 push。

### Problems
首次离线测试缺少新 SDK 的运行时依赖，联网补齐后通过。观察到 macOS Netty 原生 DNS resolver 的非阻断警告，本次实际模型与依赖请求成功；未额外增加平台依赖。

### Decision
使用 2.0.1 builder/options 显式接线，避免 1.x API 混用和多个供应商共享配置；不新增公开的付费模型调试 HTTP 入口，不在模型适配器内实现用户手敲的 Context Assembly。

### Next
Agent 实现 embedding 批量写回与独立定维/HNSW migration，随后讲解并由用户手敲 PgVectorDenseRetriever 核心查询流程。


## 2026-09-05 13:54 CST

### Task
按用户最新要求加速交付，保留核心手敲，其余由 Agent 完成；暂停长篇教学。

### Changes
新增 DocumentEmbeddingService 与 Flyway V2：vector(1024)、embedding_model、cosine HNSW。模型计算不进入事务，短事务锁 document 并核对 hash 和 batch update 数量，防止旧快照写回；已全部索引文档直接跳过。新增 PostgreSQL 并发场景测试与真实模型入库测试。保留用户 DocumentContentHasher 的解释注释。

### Result
向量入库基础设施完成。下一个用户手敲模块为 PgVectorDenseRetriever，尚未生成该核心类。

### Verification
- 迁移前非空向量为 0；V1、V2 均 success，HNSW 索引存在。
- RUN_POSTGRES_TESTS=true RUN_MODEL_TESTS=true mvn test：23 tests 全部通过，0 failures、0 errors、0 skipped。
- 真实百炼生成 1024 维向量并写入 PostgreSQL；再次索引返回 ALREADY_INDEXED。
- 可控模型场景确认外部调用时无数据库事务；调用期间替换文档后旧向量写回被拒绝，新 chunk 不含错误向量。
- 专用 integration-test 文档残留为 0；git diff --check 通过。
- 未启动额外长进程，未 commit/push，原有容器保留。

### Problems
无入库阻塞。完整 Dense RAG、评测与简历成果尚未完成，不能提前标记为已交付。

### Decision
用户明确选择保留核心手敲，非核心加速。核心模块只给最小必要说明和可直接输入的代码；真实测试及成果留存持续执行。

### Next
用户完成 Dense Retriever；Agent 接上演示数据、请求入口与验证，再推进引用与最小 RAG。

## 2026-09-05 17:06 CST

### Task

用户将剩余实现全部交给 Agent，并要求在 agent.md 记录学习状态；快速完成可演示的 Retrieval、Evaluation 与 Agentic 诊断主链路。

### Changes

- 在 agent.md 标记用户已手敲/看懂 Retrieval 契约、Dense Retriever 与模型配置基础，剩余模块改为项目完成后集中学习。
- 实现 PostgreSQL Lexical Retriever、RRF Hybrid、检索路由服务、上下文预算、引用编号存在性校验、证据不足降级和诊断 HTTP。
- 加入 `demo-v1` 合成语料、幂等 seed、Recall@K/MRR/nDCG、逐题结果落盘和评测读取 API。
- 新增 V3 事故、部署、状态和变更事实表；实现确定性 Router、五个只读工具、5 步/15 秒/重复签名约束的 Agent Loop 及 HTTP API。
- 增加统一安全错误响应、请求长度/TopK 校验与默认 127.0.0.1 绑定；更新 README、RAG/API/Agent/Evaluation/面试文档。

### Result

Phase 1 Dense RAG 完成；Phase 2 已完成 Lexical、Hybrid/RRF 和前三路真实评测；Phase 3 已完成规则 Router、五只读工具与有界 Agent 主链路。Rerank、Agent 引用校验、SSE 与生产形态仍待继续，项目尚未宣称全部完成。

### Verification

- 完整 `RUN_POSTGRES_TESTS=true RUN_MODEL_TESTS=true mvn test`：30 tests，0 failures，0 errors，0 skipped。
- `demo-v1`：6 个合成文档、12 个可回答问题、2 个无答案问题；42 条逐模式结果已保存。Dense/Hybrid 的 Recall@3、MRR@3、nDCG@3 均为 1.0；Lexical 均为 0.5。小样本限制已写入产物。
- 真实 HTTP RAG 返回 `REFERENCES_VALIDATED`；真实 Agent 路由 AGENTIC，五工具均 SUCCESS，5 steps，terminalReason=`ANSWERED`。演示结果保存于 artifacts。
- Flyway V1/V2/V3 均 success；integration-test 文档残留 0。
- `mvn -DskipTests package` 成功，生成 128 MB 可运行 JAR。
- `git diff --check` 与已跟踪文件密钥模式扫描通过；临时应用已停止，未 commit/push。

### Problems

首次路由测试暴露“什么是”未匹配 DIRECT，已补规则并完整复测。首次离线打包缺 Maven 插件，联网补齐后成功。当前合成数据规模不能支撑生产质量或性能结论。

### Decision

优先建立可运行、可演示、可评测和可解释的主链路。Agent 当前采用确定性编排和模型结果合成，简历中不写成模型自主规划；所有效果数字必须附小型合成集限制。

### Next

实现 Rerank 对照、Agent Evidence Verification、SSE、可靠性/观测与最终演示脚本，再进入项目集中学习和简历定制。

## 2026-09-05 17:13 CST

### Task

继续完成 Rerank、Agent 工具引用校验、SSE 和投递前项目包装。

### Changes

- 新增 lightweight rerank v1：结合 query 的标识符/中文二元词重合度与归一化 RRF 分数重排候选；明确不是 cross-encoder。
- Evaluation 扩展为 Dense、Lexical、Hybrid/RRF、Hybrid+lightweight rerank 四路和 56 条逐 case 原始结果。
- Agent 工具事实使用 `T1...Tn` 编号，模型回答必须引用；无引用或未知引用降级，响应仅返回实际引用工具事实。
- 新增 Java 21 virtual-thread SSE 端点，真实输出 `started → completed` 生命周期事件；明确尚非 token streaming。
- 新增一键演示脚本和简历/面试口径文档，更新 README、设计、计划和面试笔记。

### Result

Rerank 对照、Agent Evidence Verification、生命周期 SSE 与第一版项目包装完成。当前主链路可打包、启动、演示和评测；下一阶段集中在 rate limit、Redis 运行状态、metrics/tracing 和安全回归。

### Verification

- 完整 Java/PostgreSQL/真实模型回归：32 tests，0 failures，0 errors，0 skipped。
- 四路评测最新运行：Dense 1.0/1.0/1.0，Lexical 0.5/0.5/0.5，Hybrid 1.0/1.0/1.0，Hybrid+rerank 1.0/1.0/1.0（Recall@3/MRR@3/nDCG@3）；Rerank p95 为 421 ms，无质量收益，未设默认。
- SSE 真实事件包含 `started` 和 `completed`，完成响应为 `REFERENCES_VALIDATED`。
- Agent 真实演示为 AGENTIC、5 工具 SUCCESS、9 条实际引用、`REFERENCES_VALIDATED`、5 steps、`ANSWERED`。
- `bash -n scripts/demo.sh`、`git diff --check` 通过；最新代码重新打包成功。
- 临时应用已停止；未 commit/push。

### Problems

无当前阻塞。评测仍是小型合成集，Rerank 没有可证明收益；SSE 目前是生命周期事件而非逐 token 输出。

### Decision

保留 Rerank 作为可比较实现但不设默认，不在简历中称作 cross-encoder。简历与面试口径只描述已验证能力，并明确合成数据限制和确定性 Agent 编排。

### Next

实现 Redis Agent 运行状态、rate limit、业务 metrics/tracing 与安全回归，完成最终交付后按 agent.md 的学习顺序集中讲解并定制简历。

## 2026-09-05 18:05 CST

### Task

接手后先检查首版 Agent/SSE/可靠性的真实限制，再补完预算传播、Redis 可靠性、可观测性、行为评测与交付材料。

### Changes

- Agent 预算建模为 `AgentBudget`：max-steps 5、总预算 20s、单工具 6s、回答预留 8s、上下文 6000 字符、单条事实 600 字符、事实时间窗口 90 天，全部可通过 `incidentpilot.agent.*` 覆盖。
- 工具在虚拟线程 executor 上执行并受 `Future.get(timeout)` 约束，超时 `cancel(true)`；最终回答同样受剩余预算约束。
- `DiagnosticTool` 契约重写：新增 `ToolStatus`（SUCCESS/EMPTY/SKIPPED_MISSING_PARAMETER/TIMEOUT/FAILED）与带来源、时间的 `ToolFact`；`ToolResult` 在构造期就拒绝非 SUCCESS 携带事实。缺 serviceName 的提示文本不再进入证据。
- 动态事实工具加入显式时间窗口过滤，事实文本携带来源表与 UTC 时间戳。
- `QueryRouter` 改为企业事实信号优先：服务名 / 版本号 / 动态事实词先判 AGENTIC，再判解释型 DIRECT，修复"payment-service 的 5xx 是什么原因"被直答的问题。DIRECT 路由改为真实调用模型给出通用解释并声明未使用企业数据。
- 新增 Redis 可靠性层：带 TTL 的 Agent 运行状态（不持久化回答正文）、固定窗口限流、在途并发控制，Redis 故障 fail-open 并计入降级指标。新增 `GET /api/v1/agent/runs/{runId}`。
- SSE emitter 超时/错误回调绑定后台任务 `cancel(true)` 并归还并发许可；事件加入 requestId 与 sequence；超时时长改为可配置。
- 新增 `RequestCorrelationFilter`（MDC + 响应头 + 错误体共用 requestId，只接受 UUID 形状的传入头）、审计日志与六类业务指标；错误响应新增 429。
- 新增行为评测 `POST /api/v1/evaluations/runs/behavior`：无答案拒绝、文档注入、查询注入、Agent 路由与引用共 6 个 case，对抗性文档运行时临时摄取、结束即删除。
- 演示脚本扩展为 8 步全链路；更新 Agent/API/安全/评测设计、开发计划与 ADR-009/010/011。

### Result

Phase 3 与 Phase 4 主要项完成，MVP 可交付。首版声称的"15 秒 deadline"在这轮之前只是工具之间的检查，现在是可测试的完整预算；限流、并发、SSE 取消、运行状态都有真实运行证据。

### Verification

- 完整回归 `RUN_POSTGRES_TESTS=true RUN_MODEL_TESTS=true mvn test`：56 tests，0 failures，0 errors，0 skipped（上一轮 32）。
- 单工具超时：慢工具记 `TIMEOUT`、不产出证据、后续工具仍执行。步进 Clock 验证预算耗尽后 `loopTermination=DEADLINE_EXCEEDED`。上下文超预算验证 `CONTEXT_BUDGET_EXHAUSTED`。
- 缺 serviceName 时 `verifyNoInteractions(jdbc)` 证明未触库；PostgreSQL 集成测试证明 400 天前的记录被时间窗口排除、窗口内无记录返回 `EMPTY` 而不是伪造事实。
- 真实 HTTP 并发验证：12 并发请求恰好 4 个 200、8 个 `429 CONCURRENCY_LIMITED`；同窗口 26 并发请求出现 6 个 `429 RATE_LIMITED`。
- 真实 HTTP SSE 验证（`sse-timeout=1s`）：1.3 秒结束、日志出现取消、Redis 并发计数回到 0、`sse.terminated{reason=TIMEOUT}=1`、`rag.diagnosis` 无完成记录，证明后台任务被真正取消。
- requestId：合法 UUID 头透传，`../../etc/passwd` 被替换为新 UUID。
- 四路检索评测重跑 `c70b433d`：Dense/Hybrid/Hybrid+rerank 的 Recall@3/MRR@3/nDCG@3 均 1.0，Lexical 0.5；质量与上轮一致，延迟抖动明显（Dense p95 从 232 ms 变为 468 ms）。
- 行为评测 `behavior-dce0564d`：6/6 通过。
- `scripts/demo.sh` 八步全链路真实执行通过；`mvn -DskipTests package` 生成 128 MB 可运行 JAR。
- 未 commit/push；本轮启动的应用已停止，PostgreSQL/Redis 容器保留。

### Problems

行为评测第一次运行暴露真实缺陷：语料外问题的回答以"证据不足"开头却顺带列出 `[E1][E2][E3]`，只做引用编号存在性校验的实现把它判成了 `REFERENCES_VALIDATED`。已按 ADR-011 修复为拒答优先，并补单测；修复后复跑 6/6 通过。

客户端中途断开 SSE 时，若此刻没有写操作，断开要到下一次 `send` 才被发现，已在途的模型调用仍会跑完（结果被丢弃、许可被归还）。emitter 超时路径已实测能真正取消。该边界已写入安全文档，不作为"完全取消"宣传。

### Decision

ADR-009 预算一等对象化并强制传播；ADR-010 Redis 承担运行状态/限流/并发并 fail-open；ADR-011 显式拒答优先于引用校验。Metadata filter、Parent-child chunk、模型原生 Tool Calling、cross-encoder rerank、tracing、MCP 全部标注为明确延期，写入开发计划表格，不静默当作完成。

### Next

按 `agent.md` 的"HTTP 请求 → Router/Agent → Tool/Retrieval → PostgreSQL/模型 → Citation/Evaluation"顺序带用户集中学习整个项目；简历与面试口径见 `docs/13-resume-project.md`。
