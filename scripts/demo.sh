#!/usr/bin/env bash
# 端到端演示：健康检查 → seed → RAG → Agent → 运行状态 → SSE → 检索评测 → 行为评测。
# 前置条件：docker compose 已启动 PostgreSQL/Redis，应用以 models profile 运行。
set -euo pipefail

base_url="${INCIDENTPILOT_URL:-http://127.0.0.1:8080}"
out_dir="${INCIDENTPILOT_ARTIFACTS:-artifacts}"
mkdir -p "$out_dir"

step() { printf '\n=== %s ===\n' "$1"; }

step "1/8 依赖健康"
curl --fail --silent --show-error "$base_url/api/v1/system/dependencies"
printf '\n'

step "2/8 导入合成演示语料（幂等）"
curl --fail --silent --show-error -X POST "$base_url/api/v1/demo/seed" | tee "$out_dir/demo-seed.json"
printf '\n'

step "3/8 带引用的 Hybrid RAG 诊断"
curl --fail --silent --show-error -X POST "$base_url/api/v1/diagnoses" \
  -H 'Content-Type: application/json' \
  -d '{"query":"payment-service v3.2.1 发布后 5xx 增加，连接池占满，应该按什么顺序排查？","topK":3,"mode":"hybrid"}' \
  | tee "$out_dir/demo-diagnosis.json"
printf '\n'

step "4/8 五工具有界 Agent 诊断"
curl --fail --silent --show-error -X POST "$base_url/api/v1/agent/diagnoses" \
  -H 'Content-Type: application/json' \
  -d '{"query":"payment-service v3.2.1 发布后 5xx 增加，请结合历史事故、部署、当前状态和变更分析原因及排查顺序"}' \
  | tee "$out_dir/demo-agent-diagnosis.json"
printf '\n'

step "5/8 读取 Redis 中带 TTL 的 Agent 运行状态"
run_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["runId"])' "$out_dir/demo-agent-diagnosis.json")"
curl --fail --silent --show-error "$base_url/api/v1/agent/runs/$run_id" | tee "$out_dir/demo-agent-run-state.json"
printf '\n'

step "6/8 生命周期 SSE（started -> completed）"
curl --fail --silent --show-error -N -X POST "$base_url/api/v1/diagnoses/stream" \
  -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d '{"query":"网关 504 但下游仍在执行，应该怎么排查？","topK":3,"mode":"hybrid"}' \
  | tee "$out_dir/demo-sse.txt"
printf '\n'

step "7/8 四路检索对照评测"
curl --fail --silent --show-error -X POST "$base_url/api/v1/evaluations/runs" \
  | python3 -c 'import json,sys; run=json.load(sys.stdin); print(json.dumps({"id":run["id"],"summaries":run["summaries"]}, ensure_ascii=False, indent=2))'
printf '\n'

step "8/8 行为评测（无答案拒绝、提示注入、Agent 路由与引用）"
curl --fail --silent --show-error -X POST "$base_url/api/v1/evaluations/runs/behavior" \
  | python3 -c 'import json,sys; run=json.load(sys.stdin); print(json.dumps({"id":run["id"],"passed":run["passed"],"total":run["total"],"cases":[{k:c[k] for k in ("caseId","type","passed","evidenceStatus","route","terminalReason","violations")} for c in run["cases"]]}, ensure_ascii=False, indent=2))'
printf '\n\n完整逐题结果见 artifacts/evaluations/\n'
