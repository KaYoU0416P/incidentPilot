package com.incidentpilot.common.config;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import com.sun.net.httpserver.HttpServer;
import com.incidentpilot.answer.AnswerGenerator;
import com.incidentpilot.knowledge.embedding.TextEmbedder;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class ModelConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ModelConfiguration.class)
            .withBean(ObservationRegistry.class, () -> ObservationRegistry.NOOP);

    @Test
    void disabledWithoutModelsProfile() {
        runner.run(context -> assertThat(context).doesNotHaveBean(TextEmbedder.class)
                .doesNotHaveBean(AnswerGenerator.class));
    }

    @Test
    void missingKeyFailsAtStartup() {
        runner.withPropertyValues("spring.profiles.active=models",
                "incidentpilot.models.chat.base-url=http://localhost",
                "incidentpilot.models.chat.api-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void separatesCredentialsAndPathsAndBatchesEmbeddings() throws Exception {
        var mapper = JsonMapper.builder().build();
        var requests = Collections.synchronizedList(new ArrayList<String>());
        var failures = Collections.synchronizedList(new ArrayList<Throwable>());
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                requests.add(path);
                var body = mapper.readTree(exchange.getRequestBody().readAllBytes());
                String json;
                if (path.equals("/chat/completions")) {
                    assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fake-chat-key");
                    assertThat(body.path("model").asText()).isEqualTo("deepseek-v4-flash");
                    assertThat(body.path("thinking").path("type").asText()).isEqualTo("disabled");
                    json = """
                        {"id":"test","object":"chat.completion","created":1,"model":"deepseek-v4-flash",
                        "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],
                        "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """;
                } else {
                    assertThat(path).isEqualTo("/compatible-mode/v1/embeddings");
                    assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fake-embedding-key");
                    assertThat(body.path("model").asText()).isEqualTo("text-embedding-v4");
                    assertThat(body.path("dimensions").asInt()).isEqualTo(1024);
                    assertThat(body.path("encoding_format").asText()).isEqualTo("float");
                    int size = body.path("input").size();
                    assertThat(size).isBetween(1, 10);
                    var entries = new ArrayList<String>();
                    for (int i = size - 1; i >= 0; i--) {
                        entries.add("{\"object\":\"embedding\",\"index\":" + i
                                + ",\"embedding\":[" + (i + 1) + "," + "0,".repeat(1022) + "0]}");
                    }
                    json = "{\"object\":\"list\",\"model\":\"text-embedding-v4\",\"data\":["
                            + String.join(",", entries) + "],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
                }
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Throwable failure) {
                failures.add(failure);
                exchange.sendResponseHeaders(500, -1);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            configured("http://127.0.0.1:" + server.getAddress().getPort()).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(AnswerGenerator.class).generate("Reply OK", "Test")).isEqualTo("OK");
                var vectors = context.getBean(TextEmbedder.class).embed(
                        IntStream.range(0, 11).mapToObj(i -> "text " + i).toList());
                assertThat(vectors).hasSize(11);
                assertThat(vectors.get(0)[0]).isEqualTo(1);
                assertThat(vectors.get(9)[0]).isEqualTo(10);
                assertThat(vectors.get(10)[0]).isEqualTo(1);
            });
            assertThat(failures).isEmpty();
            assertThat(requests).containsExactly("/chat/completions",
                    "/compatible-mode/v1/embeddings", "/compatible-mode/v1/embeddings");
        } finally { server.stop(0); }
    }

    @Test
    void providerFailurePropagatesWithoutRetry() throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            configured("http://127.0.0.1:" + server.getAddress().getPort()).run(context ->
                    assertThatThrownBy(() -> context.getBean(TextEmbedder.class).embed(List.of("test")))
                            .isInstanceOf(RuntimeException.class));
            assertThat(requests.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    private ApplicationContextRunner configured(String base) {
        return runner.withPropertyValues("spring.profiles.active=models",
                "incidentpilot.models.chat.base-url=" + base,
                "incidentpilot.models.chat.api-key=fake-chat-key",
                "incidentpilot.models.chat.model=deepseek-v4-flash",
                "incidentpilot.models.embedding.base-url=" + base + "/compatible-mode/v1",
                "incidentpilot.models.embedding.api-key=fake-embedding-key",
                "incidentpilot.models.embedding.model=text-embedding-v4",
                "incidentpilot.models.embedding.dimensions=1024");
    }
}
