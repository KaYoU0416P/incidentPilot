package com.incidentpilot.knowledge.embedding;

import java.util.List;

/** Returns one vector per input, preserving input order. */
public interface TextEmbedder {
    List<float[]> embed(List<String> texts);
}
