# 安全与可靠性

## 威胁边界

用户输入、文档内容、工具返回和外部模型输出均不可信。企业文档中的指令不得提升权限或改变 system/tool policy。

## MVP 控制

- Prompt injection：分隔数据与指令、来源标记、拒绝文档内工具指令。
- Tool injection：工具 allowlist、只读权限、schema 校验、服务/时间范围限制。
- 参数：Bean Validation、最大长度/条数、规范化 service/version。
- 超时/重试：全局 deadline、单调用 timeout；仅瞬态错误指数退避并限制次数。
- Rate limit：Redis 计数/令牌桶，Redis 故障时采用明确降级策略。
- Audit：记录 route/tool/evidence/config version，不记录 secret 和完整敏感正文。
- Secrets：只从环境或 secret manager 注入，`.env` 不提交。
- Hallucination：结构化输出、引用校验、证据不足状态。

## 可靠性目标

外部模型不可用时基础健康接口仍可用；模型路径返回稳定错误码。PostgreSQL/Redis 故障不能产生伪造答案。文档与检索索引写入处于 PostgreSQL 一致性边界；Embedding 外部调用采用可重试 job 与短事务写入，不把远程模型调用伪装成数据库事务。

具体 timeout、retry、rate limits 在负载验证后登记；当前不声称生产级 SLA。

## 已实现控制与真实验证（2026-09-05）

| 控制 | 实现 | 验证方式与结果 |
| --- | --- | --- |
| Agent 总预算 | `AgentBudget.total-budget=20s`，剩余预算低于 `answer-reserve=8s` 停止调用工具 | 单测用步进 Clock 验证 `loopTermination=DEADLINE_EXCEEDED` |
| 单工具超时 | `tool-timeout=6s`，虚拟线程 `Future.get(timeout)` + `cancel(true)` | 单测：慢工具记 `TIMEOUT`、不产出证据，后续工具仍可执行 |
| 上下文预算 | `context-chars=6000`、`fact-chars=600` | 单测：超预算时 `loopTermination=CONTEXT_BUDGET_EXHAUSTED` |
| 工具失败隔离 | `ToolStatus` 区分 SUCCESS/EMPTY/SKIPPED/TIMEOUT/FAILED，非 SUCCESS 不产生证据 | 单测 + `ToolResult` 编译期约束（非 SUCCESS 携带事实直接抛错） |
| 拒绝宽表扫描 | 动态事实工具缺 `serviceName` 直接 `SKIPPED_MISSING_PARAMETER` | 单测 `verifyNoInteractions(jdbc)` 证明未触库 |
| 时间窗口 | 动态事实强制 `>= now() - fact-lookback(90d)` | PostgreSQL 集成测试：400 天前记录被排除，90 天内记录返回 |
| 限流 | Redis 固定窗口，`rate-permits=20 / 60s` | 真实 HTTP：同窗口 26 并发请求，6 个返回 `429 RATE_LIMITED` |
| 并发控制 | Redis 计数器 + 兜底 TTL，`max-concurrent=4` | 真实 HTTP：12 并发请求，恰好 4 个 200、8 个 `429 CONCURRENCY_LIMITED` |
| SSE 取消 | emitter 超时/错误回调 `cancel(true)` 并归还并发许可 | 真实 HTTP（`sse-timeout=1s`）：日志出现取消、Redis 并发计数回到 0、`sse.terminated{reason=TIMEOUT}=1`、`rag.diagnosis` 无完成记录 |
| 运行状态 | Redis `incidentpilot:agent:run:{runId}`，TTL 1h，不持久化回答正文 | Redis 集成测试：可读回、TTL 在范围内、原文不出现在缓存值中 |
| requestId 关联 | `RequestCorrelationFilter` 写 MDC/响应头，只接受 UUID 形状的传入头 | 真实 HTTP：合法头透传，`../../etc/passwd` 被替换为新 UUID |
| 提示注入 | 指令与数据分离 + 引用校验 + 显式拒答降级 | 行为评测：文档注入与查询注入两个 case 均未输出 canary、未泄漏 system prompt |

### fail-open 决策

Redis 不可用时限流与并发控制选择 fail-open，记录 `incidentpilot.request.guard_degraded` 指标与告警日志。本地单实例 MVP 优先保证可用性；面向公网部署必须改为 fail-closed，该取舍已在 ADR-010 登记。

### 已知边界

- 预算是墙钟约束。工具线程被 `interrupt` 后，底层 JDBC/HTTP 驱动是否立即返回不由本项目保证；保证的是请求线程不再等待、被取消的结果不进入上下文。
- SSE 客户端中途断开时，若此刻没有写操作，断开可能到下一次 `send` 才被发现；这种情况下已在途的模型调用仍会跑完，只是结果被丢弃、许可被归还。emitter 超时路径已实测能真正取消。
- 限流是固定窗口，不是令牌桶，窗口边界可能出现两倍瞬时速率。
- 注入防护通过的是当前 6+1 份合成语料上的少量 case，不能推广为“对任意提示注入安全”。
