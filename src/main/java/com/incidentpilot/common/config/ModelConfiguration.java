package com.incidentpilot.common.config;

import java.time.Duration;
import java.util.Map;
import com.incidentpilot.answer.AnswerGenerator;
import com.incidentpilot.answer.SpringAiAnswerGenerator;
import com.incidentpilot.knowledge.embedding.TextEmbedder;
import com.incidentpilot.knowledge.embedding.SpringAiTextEmbedder;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false) //  不需要 Spring 给这个配置类加一层代理，来拦截配置方法之间的直接调用
@Profile("models")
public class ModelConfiguration {
    @Bean
    OpenAiChatModel deepSeekChatModel(Environment env, ObservationRegistry observations) {
        return OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
                .baseUrl(required(env, "chat.base-url"))
                .apiKey(required(env, "chat.api-key"))
                .model(required(env, "chat.model"))
                .timeout(Duration.ofSeconds(30)).maxRetries(0)
                .maxTokens(1024)
                .extraBody(Map.of("thinking", Map.of("type", "disabled")))
                .build()).observationRegistry(observations).build();
    }

    @Bean
    OpenAiEmbeddingModel bailianEmbeddingModel(Environment env, ObservationRegistry observations) {
        return OpenAiEmbeddingModel.builder().options(OpenAiEmbeddingOptions.builder()
                .baseUrl(required(env, "embedding.base-url"))
                .apiKey(required(env, "embedding.api-key"))
                .model(required(env, "embedding.model"))
                .dimensions(env.getRequiredProperty("incidentpilot.models.embedding.dimensions", Integer.class))
                .encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
                .timeout(Duration.ofSeconds(30)).maxRetries(0)
                .build()).observationRegistry(observations).build();
    }

    @Bean
    TextEmbedder textEmbedder(OpenAiEmbeddingModel model, Environment env) {
        return new SpringAiTextEmbedder(model,
                env.getRequiredProperty("incidentpilot.models.embedding.dimensions", Integer.class));
    }

    @Bean
    AnswerGenerator answerGenerator(OpenAiChatModel model) {
        return new SpringAiAnswerGenerator(model);
    }

    private static String required(Environment env, String suffix) {
        String key = "incidentpilot.models." + suffix;
        String value = env.getProperty(key);
        Assert.hasText(value, "Missing configuration: " + key);
        return value;
    }
}
