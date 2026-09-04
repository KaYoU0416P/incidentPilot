# IncidentPilot 项目总纲

## 项目定位

IncidentPilot 是面向企业研发与运维知识的故障诊断 Agent / Agentic RAG 系统。它以 Java 后端为主体，把检索、工具调用、上下文构造和评测作为可解释、可替换的应用能力，而不是隐藏在 Spring AI 黑盒中。

## 核心场景

典型输入：`payment-service 在 v3.2.1 发布以后 5xx 明显增加，请结合历史事故、发布记录和服务状态分析最可能原因，并给出排查顺序。`

系统根据问题复杂度选择：

- Direct：不依赖企业事实的简单解释。
- Retrieval：单次知识检索可以回答的问题。
- Agentic：需要组合历史事故、部署、服务状态、变更记录等动态数据的问题。

输出必须包含结论、证据、引用、建议排查顺序；证据不足时明确拒绝猜测。

## 一周 MVP

1. 可复现的 Dense RAG baseline 与引用。
2. PostgreSQL Lexical Retrieval、Dense、RRF、Rerank 组成的 Hybrid Retrieval。
3. 带 ground truth 的评测集，真实比较各检索方案。
4. Direct / Retrieval / Agentic 路由和受控 Agent Loop。
5. 五个只读诊断工具：知识、事故、部署、状态、变更查询。
6. SSE、超时/重试、审计、基础指标。
7. PostgreSQL + pgvector、Redis 的 Docker Compose 本地环境。
8. 可演示数据、脚本和完整文档。

## 非目标

第一周不实现 GraphRAG、复杂 Multi-Agent、Kafka、Kubernetes、Fine-tuning、多模态、复杂权限系统、自部署大模型和复杂前端。MCP 仅在 Phase 1～3 稳定后评估；若第 4 天 Hybrid + Evaluation 未跑通，明确延期 MCP。

## 锁定技术栈

- Java 21
- Spring Boot 4.1.1
- Spring AI 2.0.1 Stable（BOM 锁定，不使用 Snapshot）
- Maven
- PostgreSQL 17、pgvector 0.8.6、Redis 8（容器镜像锁定明确 patch 版本）
- 单体模块化后端；必要时提供极简 Demo UI

版本依据：Spring AI 2.0.x 官方支持 Spring Boot 4.0.x 与 4.1.x；Spring Boot 4.1.1 支持 Java 17～26，因此 Java 21 兼容。每次首次采用新的 Spring AI API，都必须在对应设计或日志中登记官方 Reference/Javadoc 链接。

## 核心亮点

- 框架无关的 `Retriever`、`QueryRouter`、`Tool`、`ContextAssembler`、`EvidenceVerifier` 抽象。
- 保留 baseline，以指标而不是直觉决定 Hybrid、Rerank 等优化是否保留。
- Retrieval as Tool：静态知识与动态运行数据分开获取，再进行受预算约束的上下文构造。
- 可观测的 Agent 轨迹和引用，不暴露模型隐式思维过程。
- 文档即项目状态；聊天记录不作为事实源。

## 项目边界与成功标准

系统提供诊断建议，不自动执行发布、回滚或生产写操作。成功标准是：可构建、可启动、可通过 API 演示、有真实评测结果、有可追溯决策，并能解释核心调用链和权衡。

## 当前状态

PostgreSQL + pgvector 架构迁移已完成，当前进入 Phase 1 Dense Retrieval Baseline。真实进度与门禁见 [09-development-plan.md](09-development-plan.md)，历史技术路线见 [11-decisions.md](11-decisions.md)。
