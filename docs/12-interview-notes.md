# 面试知识笔记

## 模块化单体

### 一句话解释

保持单进程部署，同时用明确业务模块和 port/adapter 获得接近微服务的边界。

### 为什么项目使用

一周 MVP 的主要风险是检索效果与 Agent 可控性，不是服务吞吐；模块化单体减少分布式事务和部署噪声。

### 面试官可能追问

如何防止模块互相乱依赖？回答应覆盖 package-by-feature、领域 port、架构测试、禁止跨模块直接访问 repository。

## Dense vs Lexical

### 一句话解释

Dense 用向量捕捉语义相似；Lexical 用词项匹配捕捉精确关键词。本项目首版 Lexical 是 PostgreSQL FTS，不等同于标准 BM25。

### 为什么项目两者都要

故障问题既有语义表达，也包含 `payment-service`、`v3.2.1`、错误码等必须精确匹配的标识符。

### 面试官可能追问

为什么不能直接加两种 score？因为分数空间和尺度不同；项目用基于排名的 RRF 避免先做分数校准。

## RRF

### 一句话解释

Reciprocal Rank Fusion 用各检索器的名次而非原始分数融合候选：`sum(1/(k+rank))`。

### Trade-off

简单、鲁棒、无需校准；但忽略原始分数间距，参数与候选深度仍需评测。

## Agent vs Workflow

### 一句话解释

Workflow 的步骤由代码预先确定；Agent 让模型在受控动作集合中动态选择下一步。

### 本项目回答

路由和安全边界是 workflow；只有多源诊断内部使用有步数、时间、上下文预算的 Agent Loop。模型提出 tool call，应用负责校验和执行。

## Spring AI 在项目中的角色

### 一句话解释

Spring AI 提供模型、Embedding、VectorStore、Tool Calling 和结构化输出适配，但不拥有业务检索策略和 Agent 生命周期。

### 面试官可能追问

移除 Spring AI 怎么办？替换 adapter；Retriever、RRF、Router、Agent state、Evaluation 指标仍留在领域/应用层。

## Retrieval 核心契约

### 一句话解释

`Retriever` 是框架无关的输入输出 port：接收已校验的 `RetrievalQuery`，返回带检索器名称和有序不可变 chunk 快照的 `RetrievalResult`。

### 为什么不直接返回 Spring AI Document

Spring AI 类型属于 adapter 细节。自有契约让 pgvector、PostgreSQL FTS、Hybrid 与测试替身共享稳定语义，也避免模型或存储升级扩散到业务层。

### 面试官可能追问

为什么 `score` 不限制在 `0～1`？Dense similarity、distance、FTS rank 和 reranker score 的方向与尺度可能不同，不能直接相加；单个 retriever 内只保证排序语义，跨检索器使用 RRF 或经过评测的显式校准。

## 幂等知识摄取

### 一句话解释

`sourceKey` 回答“是不是同一个来源”，`contentHash` 回答“正文有没有变化”：相同就跳过 chunk 重写，不同就在一个短事务里替换。

### 为什么 Embedding 不放在数据库事务里

Embedding 是网络模型调用，耗时和失败都不可控。若事务一直等待它，会长期占用连接和锁；因此先在事务外批量计算，成功后再用短事务写入可重建向量。

### 面试官可能追问

为什么 Entity 实现 `Persistable.isNew()`？项目在应用侧预生成 UUID，单看非空 id 时 Spring Data 可能把新对象当成旧对象并先执行查询；显式新旧状态可以直接 `persist`，减少批量 chunk 摄取的额外 SQL。

## 待持续补充

### PostgreSQL Retrieval 知识树

按实现进度逐项深化，不提前堆八股：PostgreSQL vs MySQL、MVCC、JSONB、GIN、GiST、Full Text Search、`tsvector`、`tsquery`、pgvector、Vector Distance、HNSW、IVFFlat、Metadata Filter、Dense Retrieval、Lexical Retrieval、Hybrid Retrieval、RRF。

其余待补充：Token、Context Window、Embedding、Chunking、真正的 BM25、Reranker、Parent-Child、Context Engineering、KV Cache、Citation、Evaluation、ReAct、MCP、Prompt Injection、Tool Security，以及 Redis/SSE/线程池/超时/重试/事务/缓存/Docker/Observability。


## 双模型供应商接入（2026-09-05）

- 调用链：业务 `TextEmbedder` / `AnswerGenerator` → Spring AI adapter → 各自供应商 HTTP API。框架类型留在 adapter，Retriever 和后续 Context Assembly 不依赖供应商 DTO。
- DeepSeek 负责回答；百炼负责把查询与文档投射到相同向量空间。回答模型可以独立更换；更换 Embedding 模型需重建文档向量，不能只改查询端。
- “1024 维”是本项目固定的 baseline 参数，不是质量最优结论。批量 Embedding 必须维护 input index 对应关系，否则向量可能错配到原文。
- 2.0.1 的 OpenAI adapter 已使用官方 Java SDK，构造器使用 builder/options，不能照搬旧版路径和配置项。分别设置 base URL 与 key，防止供应商混接。
- 已验证 Spring AI 真实请求和响应；尚不能说已完成向量入库、Dense RAG、Tool Calling 或 Hybrid。

## 当前可陈述的项目能力（2026-09-05）

- PostgreSQL 同时承载关系事实、JSONB、generated tsvector、pgvector(1024) 与 HNSW；Embedding 在事务外计算，短事务锁定 document 并核对 content hash 后批量写回。
- Dense、PostgreSQL Lexical 与 RRF Hybrid 均通过自有 Retriever port。`demo-v1` 小型合成集上 Dense/Hybrid Recall@3、MRR@3、nDCG@3 为 1.0，Lexical 为 0.5；必须同时说明只有 6 文档、12 个可回答问题，不能称为生产准确率。
- Retrieval RAG 使用编号证据块并校验模型引用是否属于本次上下文；无引用或未知引用降级为证据不足。这是引用存在性校验，不是语义事实证明。
- Agent 使用可审计的确定性 Router、五个只读工具和有界循环：最多 5 步、重复签名检测。真实 HTTP 演示组合知识、历史事故、部署、服务状态和变更后生成诊断，并返回工具轨迹与 `ANSWERED` 终止原因。
- 当前不能说：使用了 cross-encoder reranker、完成生产负载测试、实现模型自主规划、完成 Agent 逐句引用校验、已经上线生产。

## 加固后新增的可陈述能力（2026-09-05 18:05）

- Agent 预算是一等对象且可测试：总预算 20s、单工具 6s（超时 `Future.cancel(true)`）、回答预留 8s、上下文 6000 字符、单条事实 600 字符、动态事实时间窗口 90 天。`loopTermination` 回答"循环为什么停"，`terminalReason` 回答"这次运行的最终结论是什么"，两者分开是为了区分"预算耗尽但仍答了"和"预算耗尽所以没答"。
- 工具状态区分 SUCCESS/EMPTY/SKIPPED_MISSING_PARAMETER/TIMEOUT/FAILED，`ToolResult` 在构造期就禁止非 SUCCESS 携带事实。首版把"serviceName 未提供"的提示文本当成有效事实喂给了模型，这是被这轮修掉的真实缺陷。
- 引用校验的边界：`REFERENCES_VALIDATED` 只证明编号存在于本次上下文，不证明语义蕴含。行为评测实测到模型会一边说"证据不足"一边列出合法编号，因此拒答检测必须前置（ADR-011）。
- Redis 承担运行状态（TTL 1h、不存回答正文）、固定窗口限流、在途并发控制，故障时 fail-open 并记录降级指标。真实 HTTP 验证：12 并发恰好 4 通过 8 个 429；同窗口 26 请求出现 6 个限流拒绝。
- SSE emitter 超时会取消后台任务并归还并发许可，实测超时后 Redis 并发计数归零且 `rag.diagnosis` 无完成记录。要如实说明的边界：客户端中途断开若此刻没有写操作，要到下一次 `send` 才被发现，在途模型调用仍会跑完，只是结果被丢弃。

### 高频追问准备

- "你的 deadline 真的生效吗？"——首版不生效，只在工具之间检查。现在用步进 Clock 的单测证明预算耗尽会终止循环，用慢工具的单测证明单工具超时会被取消且后续工具仍能执行。
- "限流为什么是固定窗口不是令牌桶？"——固定窗口能用 mock 完整单测、能用真实并发验证，实现成本低。代价是窗口边界可能出现两倍瞬时速率，这条写进了 ADR-010，面向公网要换滑动窗口。
- "Redis 挂了会怎样？"——限流与并发控制 fail-open 并记录 `guard_degraded` 指标。本地单实例优先可用性；公网部署必须改 fail-closed。这是有意识的取舍，不是遗漏。
- "怎么防提示注入？"——指令与数据分离、来源标记、引用必须属于本次上下文、显式拒答降级，加上文档注入与查询注入两个行为评测 case。但只有 6+1 份合成语料上的 6 个 case 通过，不能说"安全"。
