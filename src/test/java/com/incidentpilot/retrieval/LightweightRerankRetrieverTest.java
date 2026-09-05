package com.incidentpilot.retrieval;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LightweightRerankRetrieverTest {
    @Test void extractsIdentifiersAndChineseBigrams() {
        assertThat(LightweightRerankRetriever.terms("payment-service 连接池耗尽"))
                .contains("payment-service", "连接", "接池", "耗尽");
    }
}
