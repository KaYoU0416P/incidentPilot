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
