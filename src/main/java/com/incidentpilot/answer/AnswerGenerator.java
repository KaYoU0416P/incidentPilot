package com.incidentpilot.answer;

/** Generates text from instructions and an already assembled user context. */
public interface AnswerGenerator {
    String generate(String instructions, String userText);
}
