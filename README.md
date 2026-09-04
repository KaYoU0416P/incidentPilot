# IncidentPilot

基于 Spring AI 的企业研发故障诊断 Agent / Agentic RAG 系统。当前正从基础设施迁移进入 Dense Retrieval baseline；项目状态以 [`docs/`](docs/) 为准。

## 固定版本

- Java 21
- Spring Boot 4.1.1
- Spring AI 2.0.1 Stable
- PostgreSQL 17 + pgvector 0.8.6、Redis 8.2.9

## 本地启动

```bash
cp .env.example .env
docker compose up -d
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run
```

验证：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/system/dependencies
```

为避开开发机上的常驻服务，Compose 默认映射 PostgreSQL `15432`、Redis `16379`；容器内部仍使用标准端口 `5432/6379`。Flyway 启动时创建 `vector` extension、`document` 与 `document_chunk`。

测试：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn test
```

需要本地 PostgreSQL/pgvector 容器已启动的摄取集成测试：

```bash
RUN_POSTGRES_TESTS=true mvn -Dtest=DocumentIngestionPostgresIntegrationTest test
```

该测试在事务中验证首次创建、重复内容跳过和变化内容替换，结束后自动回滚测试数据。

`.env` 仅含本地开发配置且不会提交；不得把真实模型密钥写入仓库。
