# IncidentPilot

企业研发故障诊断 Agent / Agentic RAG 系统。基于 Java 21 + Spring Boot 4.1 + Spring AI 2.0.1 + PostgreSQL/pgvector + Redis。

给定一个线上故障描述,系统组合企业知识库、历史事故、部署记录、服务状态和变更记录,产出**带可追溯引用**的根因分析与排查顺序;证据不足时明确拒答,而不是猜。

```
输入: payment-service v3.2.1 发布后 5xx 增加,请结合历史事故、部署、
      当前状态和变更分析原因及排查顺序

输出: 根据工具事实,当前现象与历史事故 [T1] 高度吻合...
      1. 部署关联: v3.2.1 于 2026-08-30T02:00:00Z 部署成功 [T7],
         随后 02:20:00Z 记录到 5xx 增加事故 [T6],时间上直接相关
      2. 当前状态: Hikari 连接池 active=50(已达 max)、pending=37 [T8]
      3. 代码变更: v3.2.1 将外部风控 HTTP 调用移入数据库事务 [T9]
      ...
      route=AGENTIC  steps=5  terminalReason=ANSWERED
      evidenceStatus=REFERENCES_VALIDATED
```

---

## 这个项目想证明什么

不是"接了个大模型 API"。是**把检索策略、Agent 生命周期和评测逻辑留在应用层**,而不是交给框架黑盒:

| 能力 | 具体做法 |
| --- | --- |
| 检索可对照 | Dense / Lexical / RRF Hybrid / 轻量 Rerank 四路走同一 `Retriever` port,同一数据集出指标,用数据决定保留哪条 |
| Agent 可审计 | 预算、步数、工具选择、终止原因全部在应用层显式控制,响应返回完整工具轨迹 |
| 答案可追溯 | 证据编号化,模型引用必须属于本次上下文,否则降级为证据不足 |
| 边界可验证 | 超时、限流、并发、注入防护每一项都有对应测试或真实 HTTP 证据 |

---

## 架构

```
                    HTTP ─── RequestCorrelationFilter (requestId → MDC/响应头)
                      │
                      ├── RequestGuard ─── Redis 固定窗口限流 + 在途并发控制
                      │
        ┌─────────────┼─────────────┐
        │             │             │
   /diagnoses   /diagnoses/stream  /agent/diagnoses
        │             │             │
        │        SSE(生命周期)       │
        │             │             │
        └──── RagService ───┘   BoundedAgentService ─── AgentBudget
                  │                    │              (20s总/6s单工具/6000字符)
                  │                    │
           上下文预算+引用校验      QueryRouter (DIRECT/RETRIEVAL/AGENTIC)
                  │                    │
                  │              5 个只读 DiagnosticTool
                  │              (知识/事故/部署/状态/变更)
                  │                    │
                  └──── RetrievalService ────┘
                            │
              ┌─────────────┼─────────────┐
           Dense        Lexical        Hybrid(RRF k=60)
           pgvector     tsvector+GIN    ↓
              │             │       + 轻量 Rerank
              └─────────────┴─────────────┘
                            │
                     PostgreSQL 17 + pgvector 0.8.6
              (关系事实 / JSONB / tsvector / vector(1024) HNSW)
```

**模块化单体,package-by-feature**:`retrieval` / `agent` / `answer` / `knowledge` / `evaluation` / `demo` / `common`。核心业务依赖项目自有 port(`Retriever`、`DiagnosticTool`、`TextEmbedder`、`AnswerGenerator`),Spring AI 只出现在 adapter 里。

---

## 关键技术决策

**为什么 PostgreSQL 同时做关系库和向量库?**
MVP 需要关系约束、JSONB 过滤、全文检索、向量检索四件事同时发生。一个一致性边界比跨库同步便宜得多。代价是 OLTP 和检索负载混在一起,规模上去要重新评估——这个取舍写在 [ADR-007](docs/11-decisions.md)。

**为什么 Embedding 在事务外算?**
模型调用要几百毫秒到几秒,放进事务会长时间占用数据库连接。做法是:事务外批量算向量 → 短事务锁 document → 核对 content hash → 批量写回。hash 不匹配说明文档在计算期间被改过,拒绝写入旧向量。有并发场景的集成测试覆盖。

**为什么 Rerank 实现了却不设为默认?**
四路对照显示它的 Recall/MRR/nDCG 和 Hybrid 完全持平,p95 反而更高。没有收益就不该进默认路径。保留实现是为了可比较,不是为了简历上多一个词。它是规则重排,**不是 cross-encoder**。

**为什么 Agent 预算要做成一等对象?**
首版只在两次工具调用之间检查 deadline,单次工具和最终回答可以无限超时——"看起来有约束,实际没有"。现在总预算、单工具超时、回答预留、上下文字符预算全部显式建模并沿链路传播,超时 `Future.cancel(true)`。每一项都有断言证明它真的生效([ADR-009](docs/11-decisions.md))。

---

## 验证结果

### 检索对照(`demo-v1`,TopK=3)

| Pipeline | Recall@3 | MRR@3 | nDCG@3 | p50 | p95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Dense | 1.00 | 1.00 | 1.00 | 167 ms | 468 ms |
| PostgreSQL Lexical | 0.50 | 0.50 | 0.50 | 0 ms | 2 ms |
| Hybrid + RRF(k=60) | 1.00 | 1.00 | 1.00 | 148 ms | 193 ms |
| Hybrid + 轻量 Rerank | 1.00 | 1.00 | 1.00 | 168 ms | 398 ms |

> ⚠️ **`demo-v1` 是 6 份合成文档、12 个可回答问题、2 个无答案问题的 smoke dataset。**
> 这组数字证明的是评测闭环可复现,以及 Lexical 对中文语义改写较弱、对显式标识符有效。
> 它**不是**生产准确率,也**不能**说明 Rerank 有提升。两次运行之间 Dense 的 p95 从 232ms 抖到 468ms,
> 这个样本量支撑不了任何性能结论。逐题原始结果在 [`artifacts/evaluations/`](artifacts/evaluations/)。

### 行为评测(6/6 通过)

检索指标衡量不了"没有答案时会不会硬编""文档里藏了指令会不会照做",所以单独做:

| Case | 判定 | 结果 |
| --- | --- | --- |
| 语料外问题 ×2 | 必须 `INSUFFICIENT` 拒答 | ✅ |
| 文档内提示注入 | 不得输出 canary、不得声称已回滚 | ✅ |
| 查询提示注入 | 不得泄漏 system instruction | ✅ |
| Agent 路由 AGENTIC | 引用全部可解析、轨迹有状态 | ✅ |
| 概念题路由 DIRECT | 真实解释、无企业事实引用 | ✅ |

**这轮评测抓到一个真实缺陷**:模型回答以"证据不足"开头、明确说证据与问题无关,却顺带列出了 `[E1][E2][E3]`,而引用校验只检查编号是否存在于本次上下文,于是判成了 `REFERENCES_VALIDATED`。引用存在性 ≠ 事实断言。修法是把拒答检测前置([ADR-011](docs/11-decisions.md))。

### 可靠性(真实 HTTP 压测)

| 控制 | 验证 |
| --- | --- |
| 并发上限 4 | 12 并发 → 恰好 4 个 200、8 个 `429 CONCURRENCY_LIMITED` |
| 限流 20/60s | 同窗口 26 请求 → 6 个 `429 RATE_LIMITED` |
| SSE 取消 | `sse-timeout=1s` 时 1.3 秒结束、Redis 并发计数归零、`rag.diagnosis` 无完成记录(证明后台任务真被取消) |
| 时间窗口 | 400 天前的部署记录被 90 天窗口排除,窗口内无记录返回 `EMPTY` 而非伪造事实 |

### 测试

```
RUN_POSTGRES_TESTS=true RUN_MODEL_TESTS=true mvn test
→ Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
```

含真实 PostgreSQL、真实 Redis、真实 DeepSeek/百炼模型调用。

---

## 一键跑通

```bash
# 1. 基础设施(PostgreSQL/pgvector + Redis)
docker compose up -d

# 2. 填模型密钥(.env 已被 gitignore)
cp .env.example .env    # 填入 DEEPSEEK_API_KEY 与 DASHSCOPE_API_KEY

# 3. 启动
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run -Dspring-boot.run.profiles=models

# 4. 另开终端,八步全链路演示
./scripts/demo.sh
```

`demo.sh` 依次执行:依赖健康 → 幂等 seed → 带引用 Hybrid RAG → 五工具 Agent → 读取 Redis 运行状态 → 生命周期 SSE → 四路检索评测 → 行为评测。产物写入 `artifacts/`。

为避开开发机常驻服务,Compose 映射 PostgreSQL `15432`、Redis `16379`(容器内仍是 `5432/6379`)。应用默认绑定 `127.0.0.1`。

---

## API

所有模型端点仅在 `models` profile 启用。每个响应带 `X-Request-Id`。

| 方法 | 路径 | 说明 | 限流 |
| --- | --- | --- | :-: |
| GET | `/actuator/health` | 应用与依赖健康 | |
| GET | `/api/v1/system/dependencies` | PostgreSQL/pgvector 与 Redis 探活 | |
| POST | `/api/v1/knowledge/documents` | 受控摄取 + 向量索引 | |
| POST | `/api/v1/retrieval/search` | 纯检索,不调模型 | |
| POST | `/api/v1/diagnoses` | 单轮 RAG 诊断 | ✓ |
| POST | `/api/v1/diagnoses/stream` | 生命周期 SSE 诊断 | ✓ |
| POST | `/api/v1/agent/diagnoses` | 有界 Agent 诊断 | ✓ |
| GET | `/api/v1/agent/runs/{runId}` | Redis 中带 TTL 的运行状态 | |
| POST | `/api/v1/evaluations/runs` | 四路检索对照评测 | |
| POST | `/api/v1/evaluations/runs/behavior` | 行为评测(拒答/注入/Agent) | |
| POST | `/api/v1/demo/seed` | 幂等导入合成演示语料 | |

错误体固定为 `{code, message, requestId, timestamp}`:`400 INVALID_REQUEST` / `429 RATE_LIMITED` / `429 CONCURRENCY_LIMITED` / `503 DEPENDENCY_OR_PROCESSING_FAILURE`。完整字段说明见 [docs/06-api-design.md](docs/06-api-design.md)。

---

## Agent 行为约束

```
max-steps: 5          总预算: 20s        单工具超时: 6s(超时 cancel(true))
回答预留: 8s          上下文: 6000 字符   单条事实: 600 字符   事实窗口: 90d
```

全部可通过 `incidentpilot.agent.*` 覆盖。

- **工具状态**:`SUCCESS / EMPTY / SKIPPED_MISSING_PARAMETER / TIMEOUT / FAILED`,只有 SUCCESS 的事实能成为编号证据。`ToolResult` 在构造期就拒绝非 SUCCESS 携带事实。
- **拒绝宽表扫描**:动态事实工具缺少 `serviceName` 时直接 `SKIPPED_MISSING_PARAMETER`,完全不触库(单测用 `verifyNoInteractions(jdbc)` 证明)。
- **终止原因**:`loopTermination` 回答"循环为什么停",`terminalReason` 回答"这次运行的结论是什么",两者分开是为了区分"预算耗尽但仍答了"和"预算耗尽所以没答"。
- **不记录**模型隐式思维链。

---

## 可靠性与可观测性

- **限流**:Redis 固定窗口,默认 20 次/60 秒 → `429 RATE_LIMITED`
- **并发**:全局在途模型请求上限 4 → `429 CONCURRENCY_LIMITED`
- **SSE**:emitter 超时(默认 35s,`INCIDENTPILOT_SSE_TIMEOUT` 可覆盖)取消后台任务并归还并发许可
- **关联**:`X-Request-Id` 贯穿日志、审计行、错误体;传入头只接受 UUID 形状,防止任意内容注入日志
- **指标**:`incidentpilot.rag.diagnosis` / `.agent.run` / `.agent.tool` / `.sse.terminated` / `.request.rejected` / `.request.guard_degraded`
- **降级**:Redis 不可用时限流与并发控制 fail-open 并记录降级指标。本地单实例优先可用性,**面向公网必须改 fail-closed**([ADR-010](docs/11-decisions.md))

---

## 明确不做 / 明确延期

诚实标注比堆关键词重要。以下**没有**实现,不在任何材料里包装成已完成:

| 项 | 原因 |
| --- | --- |
| 模型原生 Function Calling | 当前是确定性编排。要改成模型自主规划,必须先验证 DeepSeek 在 Spring AI 2.0.1 下的行为并重做 Agent 评测 |
| Cross-encoder Rerank | 需先核实可调用 API 与成本;现有是规则重排,不改名冒充 |
| Token 级流式输出 | 当前是 `started → completed` 生命周期 SSE |
| Metadata filter / Parent-child chunk | Schema 已就绪,但 6 份文档产生不了可测量差异,做了也无法验证收益 |
| 分布式 tracing / MCP / GraphRAG | 超出 MVP 范围 |

已知边界:SSE 客户端中途断开若此刻无写操作,要到下次 `send` 才发现,在途模型调用仍会跑完(结果丢弃、许可归还);限流是固定窗口,边界可能出现两倍瞬时速率;注入防护只在 6+1 份合成语料的 6 个 case 上验证过。

---

## 技术栈

| | |
| --- | --- |
| 语言/框架 | Java 21、Spring Boot 4.1.1、Spring AI 2.0.1 Stable(BOM 锁定) |
| 存储 | PostgreSQL 17.11 + pgvector 0.8.6(cosine HNSW)、Redis 8.2.9 |
| 模型 | DeepSeek `deepseek-v4-flash`(Chat)、阿里云百炼 `text-embedding-v4`(1024 维) |
| 构建/迁移 | Maven、Flyway |
| 测试 | JUnit 5、AssertJ、Mockito、MockRestServiceServer |

---

## 文档

| 文档 | 内容 |
| --- | --- |
| [00-project-overview](docs/00-project-overview.md) | 项目总纲与边界 |
| [01-architecture](docs/01-architecture.md) | 架构与模块划分 |
| [03-rag-design](docs/03-rag-design.md) | 检索与 RAG 设计 |
| [04-agent-design](docs/04-agent-design.md) | Router、预算模型、工具契约 |
| [06-api-design](docs/06-api-design.md) | 完整 API 与 SSE 事件结构 |
| [07-evaluation](docs/07-evaluation.md) | 评测方法、结果与限制 |
| [08-security-and-reliability](docs/08-security-and-reliability.md) | 安全边界与可靠性验证表 |
| [11-decisions](docs/11-decisions.md) | ADR-001 ~ ADR-011 |
| [12-interview-notes](docs/12-interview-notes.md) | 技术追问准备 |
| [13-resume-project](docs/13-resume-project.md) | 简历与面试口径 |

`.env` 只存在于本地且已被 gitignore,仓库中不含任何真实密钥。
