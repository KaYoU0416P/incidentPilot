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
