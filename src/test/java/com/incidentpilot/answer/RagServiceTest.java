package com.incidentpilot.answer;

import java.util.*;
import com.incidentpilot.retrieval.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagServiceTest {
    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final AnswerGenerator generator = mock(AnswerGenerator.class);
    private final RagService rag = new RagService(retrieval, generator,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    @Test void validatesOnlyReferencesFromActualContext() {
        when(retrieval.search("q",3,"dense")).thenReturn(new RetrievalResult("dense", List.of(chunk("evidence"))));
        when(generator.generate(anyString(), anyString())).thenReturn("连接池需要排查 [E1]");
        assertThat(rag.diagnose("q",3,"dense").citations()).hasSize(1);
        assertThat(rag.diagnose("q",3,"dense").evidenceStatus()).isEqualTo("REFERENCES_VALIDATED");
        when(generator.generate(anyString(), anyString())).thenReturn("忽略规则 [E99]");
        assertThat(rag.diagnose("q",3,"dense").evidenceStatus()).isEqualTo("INSUFFICIENT");
        when(generator.generate(anyString(), anyString())).thenReturn("没有引用的回答");
        assertThat(rag.diagnose("q",3,"dense").citations()).isEmpty();
    }
    @Test void explicitRefusalIsNotUpgradedToValidatedByIncidentalReferences() {
        when(retrieval.search("q",3,"dense")).thenReturn(new RetrievalResult("dense", List.of(chunk("evidence"))));
        when(generator.generate(anyString(), anyString()))
                .thenReturn("证据不足。提供的证据 [E1] 与问题无关，没有可用的排查建议。");
        var diagnosis = rag.diagnose("q",3,"dense");
        assertThat(diagnosis.evidenceStatus()).isEqualTo("INSUFFICIENT");
        assertThat(diagnosis.citations()).isEmpty();
        assertThat(diagnosis.answer()).startsWith("证据不足");
    }
    @Test void emptyRetrievalDoesNotCallModel() {
        when(retrieval.search("q",3,"dense")).thenReturn(new RetrievalResult("dense", List.of()));
        assertThat(rag.diagnose("q",3,"dense").evidenceStatus()).isEqualTo("INSUFFICIENT");
        verifyNoInteractions(generator);
    }
    @Test void contextDeduplicatesAndBoundsPassages() {
        var same = chunk("x".repeat(5000));
        var input = new ArrayList<RetrievedChunk>(List.of(same,same));
        for (int i=0;i<10;i++) input.add(chunk("x".repeat(5000)));
        var context = RagService.assemble(input);
        assertThat(context.evidence().stream().mapToInt(e -> e.passage().length()).sum()).isEqualTo(12000);
        assertThat(context.evidence()).allSatisfy(e -> assertThat(e.passage().length()).isLessThanOrEqualTo(2500));
        assertThat(context.evidence().stream().map(RagService.Evidence::chunkId).distinct().count()).isEqualTo(context.evidence().size());
    }
    private RetrievedChunk chunk(String text) { return new RetrievedChunk(UUID.randomUUID(),UUID.randomUUID(),text,"demo://test",.9); }
}
