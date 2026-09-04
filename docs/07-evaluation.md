# Evaluation 设计

## 原则

Evaluation 优先于功能堆叠。任何“提升”必须绑定数据集版本、运行配置和真实结果。评测运行产物可复现并保留原始 per-case 结果。

## 数据集

首版覆盖 exact identifier、语义改写、时间/版本过滤、多来源诊断、无答案与对抗性内容。每条 EvalCase 包含 query、expected route、relevant source/chunk ids、关键事实、允许答案、禁止断言和数据版本。

## Retrieval 指标

- Recall@K：相关证据是否进入前 K。
- MRR：第一个相关结果的位置。
- nDCG@K：多级相关性和排序质量。
- latency p50/p95、候选数与失败率。

## Answer/Agent 指标

- Answer correctness：关键事实覆盖，优先规则/人工 rubric。
- Citation correctness：引用是否存在且支持对应断言。
- Faithfulness：答案是否只基于证据；LLM judge 只能作为辅助并固定 judge 配置。
- route/tool selection、tool success、steps、latency、token usage、estimated cost。

## 必须比较

1. Dense baseline
2. PostgreSQL FTS Lexical baseline
3. Dense pgvector + Lexical（分别报告，不把并行召回误称融合）
4. Hybrid + RRF
5. Hybrid + RRF + Rerank
6. Agentic Retrieval

所有对照使用同一版本的 `document` / `document_chunk` 数据、EmbeddingModel 与过滤条件。PostgreSQL FTS 结果只标记为 Lexical，不以 BM25 名义报告。若未来重新引入 Qdrant，使用同一数据集单独比较 PostgreSQL/pgvector 与 Qdrant，不在当前 MVP 实现。

## 当前结果

尚未建立数据集和运行评测，因此没有效果结论。Phase 1 完成 baseline 后立即建立首版数据集；第 4 天检查点未通过则停止 MCP/扩展功能。
