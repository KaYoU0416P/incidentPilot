package com.incidentpilot.agent;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryRouterTest {
    private final QueryRouter router = new QueryRouter();

    @Test
    void routesKnownShapes() {
        assertThat(router.route("什么是连接池").route()).isEqualTo(QueryRouter.Route.DIRECT);
        assertThat(router.route("连接池耗尽怎么排查").route()).isEqualTo(QueryRouter.Route.RETRIEVAL);
        assertThat(router.route("payment-service 发布后状态异常").route()).isEqualTo(QueryRouter.Route.AGENTIC);
    }

    @Test
    void enterpriseFactSignalsWinOverExplanationWording() {
        assertThat(router.route("payment-service 的 5xx 是什么原因").route()).isEqualTo(QueryRouter.Route.AGENTIC);
        assertThat(router.route("v3.2.1 发布之后的问题是什么").route()).isEqualTo(QueryRouter.Route.AGENTIC);
        assertThat(router.route("本次事故的根因是什么").route()).isEqualTo(QueryRouter.Route.AGENTIC);
    }

    @Test
    void genuineConceptQuestionsStayDirect() {
        assertThat(router.route("乐观锁和悲观锁的区别").route()).isEqualTo(QueryRouter.Route.DIRECT);
        assertThat(router.route("解释一下 HNSW 索引原理").route()).isEqualTo(QueryRouter.Route.DIRECT);
    }

    @Test
    void routeDecisionCarriesAuditableReason() {
        var decision = router.route("payment-service 发布后状态异常");
        assertThat(decision.reason()).isNotBlank();
        assertThat(decision.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> router.route("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
