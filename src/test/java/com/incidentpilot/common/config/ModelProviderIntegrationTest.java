package com.incidentpilot.common.config;

import java.util.List;
import com.incidentpilot.answer.AnswerGenerator;
import com.incidentpilot.knowledge.embedding.TextEmbedder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("models")
@EnabledIfEnvironmentVariable(named = "RUN_MODEL_TESTS", matches = "true")
class ModelProviderIntegrationTest {
    @Autowired TextEmbedder embedder;
    @Autowired AnswerGenerator generator;

    @Test
    void callsRealProvidersThroughSpringAi() {
        var vectors = embedder.embed(List.of("支付服务发布后出现错误。", "如何排查支付服务故障？"));
        assertThat(vectors).hasSize(2);
        for (var vector : vectors) {
            assertThat(vector).hasSize(1024);
            double norm = 0;
            for (float value : vector) {
                assertThat(Float.isFinite(value)).isTrue();
                norm += (double) value * value;
            }
            assertThat(norm).isGreaterThan(0);
        }
        String answer = generator.generate("只返回简短的测试确认，不要解释。", "请回复 OK");
        assertThat(answer).isNotBlank();
    }
}
