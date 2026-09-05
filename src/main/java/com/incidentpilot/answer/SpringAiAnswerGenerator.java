package com.incidentpilot.answer;

import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;

public final class SpringAiAnswerGenerator implements AnswerGenerator {
    private final ChatModel model;

    public SpringAiAnswerGenerator(ChatModel model) {
        Assert.notNull(model, "Chat model is required");
        this.model = model;
    }

    @Override
    public String generate(String instructions, String userText) {
        Assert.hasText(instructions, "Instructions must not be blank");
        Assert.hasText(userText, "User text must not be blank");
        var response = model.call(new Prompt(List.of(
                new SystemMessage(instructions), new UserMessage(userText))));
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null
                || response.getResult().getOutput().getText().isBlank()) {
            throw new IllegalStateException("Chat provider returned no answer");
        }
        return response.getResult().getOutput().getText();
    }
}
