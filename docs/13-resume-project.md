# IncidentPilot 简历与面试口径

所有表述以仓库中的代码、测试和 `artifacts/` 运行证据为准。凡是不能当场跑出来的，都不写。

## 一句话介绍

基于 Java 21、Spring Boot 4.1、Spring AI 2.0.1、PostgreSQL/pgvector 和 Redis 的企业故障诊断 Agentic RAG：用 Dense / Lexical / RRF Hybrid 三路检索加引用校验产出可追溯诊断，并用显式预算约束的有界 Agent 组合五类只读运维事实。

## 简历项目描述

**IncidentPilot｜企业故障诊断 Agentic RAG（个人项目）**

- 设计框架无关的 `Retriever` / `DiagnosticTool` / `AnswerGenerator` port，用 Spring AI 2.0.1 adapter 接入 DeepSeek Chat 与百炼 `text-embedding-v4`（1024 维），使检索策略、Agent 生命周期和评测逻辑不依赖模型供应商 SDK。
- 用 PostgreSQL 17 统一承载关系事实、JSONB、generated `tsvector` 与 pgvector：Embedding 在事务外批量计算，回写时短事务锁定 document 并核对 content hash，避免慢模型调用占用连接、也避免文档更新后把旧向量写回新 chunk；配 cosine HNSW 索引与幂等重建。
- 实现 Dense、PostgreSQL Lexical、RRF(k=60) Hybrid 与轻量 Rerank 四条检索路径，配 Recall@K / MRR / nDCG 与 56 条逐 case 原始结果留存；依据"轻量 Rerank 无质量收益且 p95 更高"的实测结果，决定不将其设为默认路径。
- 实现有界 Agent Loop 并把预算建模为一等对象：5 步上限、20 秒总预算、6 秒单工具超时（超时 `Future.cancel(true)`）、8 秒回答预留、6000 字符上下文预算；工具状态区分 SUCCESS/EMPTY/SKIPPED/TIMEOUT/FAILED，只有 SUCCESS 事实能成为编号证据，缺少服务名时拒绝对动态事实表做宽表扫描且完全不触库。
- 实现引用编号存在性校验与显式拒答优先降级：行为评测发现模型会一边说"证据不足"一边列出合法编号导致误判为已校验，改为拒答检测前置后复跑 6/6 通过。
- 用项目已有 Redis 实现带 TTL 的 Agent 运行状态、固定窗口限流与在途并发控制，Redis 故障时 fail-open 并记录降级指标；SSE emitter 超时绑定后台任务取消与许可归还，经真实 HTTP 验证并发计数归零、诊断未完成。
- 建立 requestId 全链路关联（MDC、响应头、审计日志、错误体）与六类业务指标；完整回归 56 tests 全绿，含真实 PostgreSQL、真实 Redis 与真实模型调用。

## 数字的正确说法

`demo-v1` 是 6 份合成故障文档、12 个可回答问题、2 个无答案问题的 smoke dataset。TopK=3 的真实运行（`artifacts/evaluations/c70b433d-8763-41ee-9376-329e9314b7e6.json`）：

| Pipeline | Recall@3 | MRR@3 | nDCG@3 | p50 | p95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Dense | 1.00 | 1.00 | 1.00 | 167 ms | 468 ms |
| PostgreSQL Lexical | 0.50 | 0.50 | 0.50 | 0 ms | 2 ms |
| Hybrid + RRF | 1.00 | 1.00 | 1.00 | 148 ms | 193 ms |
| Hybrid + 轻量 Rerank | 1.00 | 1.00 | 1.00 | 168 ms | 398 ms |

正确说法："在自建的小型合成评测集上完成四路真实对照，验证了评测闭环可复现，并据此拒绝了没有收益的 Rerank 默认化。" 
错误说法："检索准确率 100%"、"Rerank 提升了效果"、"p95 200ms 的生产性能"。两次运行之间 Dense 的 p95 从 232 ms 抖到 468 ms，这个样本量支撑不了任何性能结论。

可靠性数字可以直接说，因为都是真实 HTTP 压出来的：12 并发请求恰好 4 个通过、8 个 429；同窗口 26 请求出现 6 个限流拒绝；SSE 1 秒超时后并发计数归零且诊断未完成。

## 两分钟面试讲法

项目先建立 Dense baseline，再逐步加 Lexical、RRF、Rerank，每一步用同一数据集和参数留存逐题结果，用指标而不是直觉决定是否保留。数据层选 PostgreSQL + pgvector，是因为 MVP 同时需要关系约束、JSONB 过滤、全文检索和向量检索，一个一致性边界比跨库同步便宜；代价是 OLTP 与检索负载混在一起，规模上去要重新评估。

Agent 层没有把生命周期交给框架黑盒。我把预算做成了一等对象：总预算、单工具超时、回答预留、上下文字符预算都显式建模并在整条链路传播，工具跑在虚拟线程上，超时就 `cancel(true)`。这不是配置一个数字，而是每一项都有对应断言——用步进 Clock 证明预算耗尽会终止循环，用慢工具证明单工具超时会被取消并且后续工具仍能执行，用缺参数的工具证明它根本不会碰数据库。

最能说明问题的是一个真实缺陷。我加行为评测验收"没有答案时会不会硬编"，第一次跑就挂了一个 case：模型回答以"证据不足"开头、明确说证据与问题无关，但顺带列了 [E1][E2][E3]，而我的引用校验只检查编号是否存在于本次上下文，于是判成了"引用已校验"。这暴露了引用存在性和事实断言不是一回事。修法是把显式拒答检测放到引用校验之前，命中就降级为证据不足并清空引用。代价是模型如果一边说证据不足一边给有效结论也会被保守降级——在诊断场景我认为宁可少答不可错答，这个取舍写进了 ADR。

需要说清楚边界的地方：Router 和工具顺序是确定性规则，不是模型自主规划；Rerank 是规则重排，不是 cross-encoder；SSE 是生命周期事件流，不是逐 token streaming；评测是 6 份合成文档的 smoke dataset。

## 演示顺序

1. `docker compose up -d` 启动 PostgreSQL/pgvector 与 Redis，再以 `models` profile 启动应用。
2. 执行 `./scripts/demo.sh`，八步依次展示健康检查、幂等 seed、带引用 RAG、五工具 Agent、Redis 运行状态、生命周期 SSE、四路检索评测、行为评测。
3. 打开 `artifacts/demo-agent-diagnosis.json` 讲工具轨迹与 `terminalReason`，打开最新 `behavior-*.json` 讲拒答与注入验收，打开检索评测 JSON 讲四路对照与限制。
4. 如果面试官追问可靠性，现场并发打 12 个请求展示 4 通过 8 个 429，或把 `INCIDENTPILOT_SSE_TIMEOUT=1s` 重启展示 SSE 取消。

## 当前不能写进简历

- 生产上线、生产 QPS、生产准确率、大规模向量性能。
- cross-encoder 或商业 reranker；当前是 lightweight rerank v1（规则重排）。
- 模型自主规划 / 原生 Function Calling；当前 Router 与工具顺序是确定性策略。
- token 级流式输出；当前是 `started -> completed` 生命周期 SSE。
- "对提示注入安全"；当前只有 6+1 份合成语料上的 6 个行为 case 通过。
- 自动执行回滚、扩容或任何生产写操作；MVP 五个工具全部只读。
- 分布式 tracing、MCP、GraphRAG、Metadata filter、Parent-child chunk —— 见开发计划中的"明确延期"表。
