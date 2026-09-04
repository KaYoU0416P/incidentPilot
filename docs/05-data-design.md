# 数据设计

## PostgreSQL + pgvector

PostgreSQL 是 MVP 的权威持久化与检索数据库，统一承载结构化业务事实、文档元数据、chunk、JSONB metadata、Lexical Retrieval 和向量 embedding。Redis 仅保存可丢失的短期状态。

本轮只创建 Dense/Lexical baseline 真正需要的两张表，`incident`、`deployment`、`service`、`change_log` 在对应 Tool 用例进入实现时再设计，避免空 Entity 和过度 schema。

### `document`

| 字段 | 类型 | 目的 |
|---|---|---|
| `id` | UUID PK | 稳定内部标识 |
| `source_key` | TEXT UNIQUE | 来源侧幂等键 |
| `title` | TEXT | 展示与 citation |
| `document_type` | TEXT | runbook / incident / deployment 等分类 |
| `service_name` | TEXT NULL | 可选服务过滤 |
| `source_uri` | TEXT | 可追溯来源定位 |
| `content_hash` | CHAR(64) | 内容幂等与变更检测 |
| `created_at` / `updated_at` | TIMESTAMPTZ | UTC 生命周期 |

### `document_chunk`

| 字段 | 类型 | 目的 |
|---|---|---|
| `id` | UUID PK | 稳定 chunk 标识 |
| `document_id` | UUID FK | 所属文档，删除文档时级联 |
| `parent_chunk_id` | UUID NULL FK | parent-child expansion；首版允许为空 |
| `chunk_index` | INTEGER | 文档内稳定顺序 |
| `content` | TEXT | 原始证据文本，禁止空白 |
| `metadata` | JSONB | 标题路径、版本、事件时间等可演进过滤属性 |
| `embedding` | VECTOR NULL | 可重建 Dense index 数据 |
| `search_vector` | TSVECTOR generated | 由正文生成的 Lexical index 数据 |
| `created_at` / `updated_at` | TIMESTAMPTZ | UTC 生命周期 |

唯一约束 `(document_id, chunk_index)` 防止同一文档重复位置；自关联 parent 必须属于有效 chunk。领域层不直接暴露 JSONB 或 pgvector JDBC 类型。

## 索引策略

- B-tree：`document(document_type, service_name)`、`document_chunk(document_id, chunk_index)`、`parent_chunk_id`。
- GIN：`document_chunk.metadata` 使用 `jsonb_path_ops` 支撑 containment filter；`search_vector` 支撑 PostgreSQL FTS。
- HNSW：待 baseline EmbeddingModel 确定实际维度后，通过独立 Flyway migration 将维度和 cosine HNSW 索引版本化。
- 初始无维度 `vector` 列可保存不同维度并执行 exact search，但不得混合不同模型的数据参与同一次检索。

`search_vector` 首版使用 `simple` 配置，优先保证错误码、服务名和版本号不被英语词干化。中文效果必须通过 Evaluation 验证；不把 PostgreSQL FTS 宣称为 BM25。

## Redis

仅保存可丢失或可重建数据：`incidentpilot:session:{id}`、`incidentpilot:ratelimit:{subject}`、`incidentpilot:idempotency:{key}`、短期查询缓存。所有 key 必须有 TTL 和版本前缀；缓存失效不影响事实正确性。

## 生命周期与一致性

源文档以 `source_key + content_hash` 做幂等摄取。文档、chunk、metadata、lexical/embedding 更新在 PostgreSQL 事务内完成；Embedding API 调用不放在长数据库事务中，而由可重试 ingestion job 先计算、后短事务写入。更换模型或切分策略时必须记录版本并重新评测。

首版摄取服务把按 `chunk_index` 排序后的正文长度与正文内容输入 SHA-256，避免简单字符串拼接产生边界歧义。重复内容保留原 document/chunk id；内容变化时保留 document id，并整体替换其 chunk。`search_vector` 继续由 PostgreSQL generated column 自动生成，应用不手工维护。

## 本地端口

- PostgreSQL：`127.0.0.1:15432` → 容器 `5432`
- Redis：`127.0.0.1:16379` → 容器 `6379`

端口可通过环境变量覆盖，默认值避开开发机常驻服务。Compose 使用 `pgvector/pgvector:0.8.6-pg17-trixie`，即 PostgreSQL 17 与 pgvector 0.8.6。
