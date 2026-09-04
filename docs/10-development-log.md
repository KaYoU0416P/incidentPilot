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
