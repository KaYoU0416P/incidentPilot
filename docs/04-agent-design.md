# Agent 设计

## Router

输出 `DIRECT | RETRIEVAL | AGENTIC`、置信度和简短可审计理由。规则优先处理明显场景，模型分类用于语义边界；低置信度默认走 Retrieval，而不是高成本 Agentic。

## 有界 Agent Loop

```text
initialize state -> model decision -> validate tool call -> execute
-> append bounded observation -> evidence check -> answer or next step
```

循环必须有 max steps、总 deadline、单工具 timeout、上下文预算、重复调用检测和 terminal reason。工具失败只在错误可重试且剩余预算允许时重试；参数错误不重试。

## Tools

- `searchKnowledge`
- `queryIncidentHistory`
- `queryDeployment`
- `queryServiceStatus`
- `queryChangeLog`

所有 MVP 工具只读、输入使用结构化 schema、输出限制条数/字段/字节，并记录工具名、脱敏参数、耗时、结果数量和状态。工具返回数据是不可信内容，不得把其中指令当系统指令执行。

## Evidence Verification

最终结构化输出的关键事实必须映射到 Evidence。Verifier 检查引用存在性、来源类型、时间一致性和最小证据数量；它不声称完成形式化事实证明。

## MCP

MCP 延后到核心稳定后。若实现，只暴露已有 Tool port，不复制业务逻辑。MCP Server/transport/permission 的选择需独立 ADR 和端到端验证。

## 未决参数

max steps、timeout、context budget、重试次数、路由阈值均在实现与负载测试后确定，不能把占位值宣传为生产最优。
