# RAG 与检索设计

## 目标流水线

```text
parse -> parent/child chunk -> embed/index
query -> rewrite -> dense + lexical -> RRF -> rerank -> topK
      -> context assembly -> answer -> citation verification
```

## Phase 1 Baseline

先实现可复现的 Dense baseline：固定语料、chunk 策略、embedding 模型版本、PostgreSQL schema/index 版本、TopK 和 prompt。每条结果输出 rank、score、sourceId、chunkId、source locator。它是后续对照组，不会被 Hybrid 代码覆盖。

## 框架无关契约

`Retriever.retrieve(RetrievalQuery) -> RetrievalResult`。Spring AI `EmbeddingModel`/`PgVectorStore` 或直接 JDBC 位于适配器层。候选分数不跨检索器直接比较；融合由独立 `FusionStrategy` 完成。

Phase 1 的最小契约只表达检索语义，不泄漏 PostgreSQL、pgvector 或 Spring AI 类型：

- `RetrievalQuery`：原始查询文本与调用方要求的 `topK`，创建时拒绝空文本和非正数。
- `RetrievedChunk`：稳定的 `sourceId`、`chunkId`、正文、来源定位和本检索器内的分数。
- `RetrievalResult`：按排名顺序返回不可变候选列表，并保留检索器名称便于评测与审计。
- `Retriever`：只负责从查询得到检索结果；切分、索引、上下文拼装和回答生成不进入该接口。

Phase 2 的 metadata filter 通过向 `RetrievalQuery` 增加明确的值对象演进，不提前把任意 `Map` 暴露为公共契约。

## Parsing 与 Chunking

- 首批数据使用可控 Markdown/JSON，避免 PDF 解析噪声掩盖检索问题。
- child chunk 用于召回，parent section 用于补充上下文。
- chunk size、overlap、标题路径、文档类型、服务、版本、事件时间写入索引清单。
- 具体参数必须通过语料统计与评测确定，当前不预设“最佳值”。

## Phase 1 摄取边界

`DocumentIngestionService.ingest(DocumentIngestionRequest)` 接收已经完成解析和切分的文档，不负责读取 PDF、调用 EmbeddingModel 或决定 chunk 策略。请求包含稳定 `sourceKey`、来源信息和按 `chunkIndex` 排序的 chunk；服务对 chunk 正文计算 SHA-256 作为 `contentHash`。

- 首次摄取：创建 document 与全部 chunk。
- 同一 `sourceKey + contentHash`：判定正文未变化，不重复删除和插入 chunk。
- 同一 `sourceKey`、不同 `contentHash`：在同一 PostgreSQL 事务内更新 document 并替换 chunk。
- Embedding：后续在数据库事务之外批量计算，再用短事务写回，避免外部模型延迟占用数据库连接。

持久化 Entity 只映射权威字段和摄取需要的 JSONB metadata，不把数据库生成的 `search_vector` 或 pgvector JDBC 类型泄漏到领域请求。Dense Retriever 后续使用专用查询 adapter 读取 embedding 与检索结果。

## Dense 与 Lexical

Dense 使用 pgvector 距离检索，擅长语义相似和改写表达；Lexical 使用 PostgreSQL `tsvector` / `tsquery` 与 GIN，擅长服务名、版本号、错误码和显式词项。PostgreSQL 原生 FTS 不是标准 BM25，文档、代码和评测统一称为 Lexical Retrieval。中文分词质量需要用数据集验证，必要时再单独记录 tokenizer 或真正 BM25 的 ADR。

初始 migration 不猜测 embedding 维度，先使用无固定维度的 `vector` 列和 exact search。选定 baseline EmbeddingModel 后，以独立 migration 固定维度并建立 cosine HNSW 索引。Spring AI 2.0.1 的官方 starter 为 `spring-ai-starter-vector-store-pgvector`；在 EmbeddingModel 确定前不启用自动配置，避免基础启动依赖模型密钥。

## Hybrid 与 RRF

Dense、Lexical 各返回独立排名；RRF 使用 `score(d)=sum(1/(k+rank_i(d)))` 融合，避免校准异构原始分数。`k`、候选数和 TopK 都是评测参数。融合后再 rerank；若 reranker 在正确性、延迟和成本综合指标上无收益，则不进入默认路径。

## Query Rewrite 与 Metadata Filter

改写必须保留实体、版本、错误码和时间约束，同时保留原查询用于审计。metadata filter 在检索前缩小 service、documentType、version、time range；过滤条件必须来自已验证的结构化解析，而不是未经信任的自由文本。

## Context Assembly 与 Citation

按相关性、来源多样性、时间新鲜度和 token 预算选择证据；去重相邻 chunk，必要时展开 parent。上下文使用编号 Evidence Block。答案中的 citation id 必须属于本次上下文，否则结果降级为“证据不足”或删除无效断言。

## 变更登记模板

每次修改检索策略时追加：问题、变更、假设、数据集版本、对照组、指标结果、是否保留。当前尚无真实评测结果，不声明准确率或性能提升。
