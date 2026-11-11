package com.teacherhub.risk.analyzer;

import com.sun.net.httpserver.HttpServer;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonLlmRiskAnalyzerTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void exchangesExtendedSeverityContractOverHttp() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ai/analyze", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = extendedResponse().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        var analyzer = new PythonLlmRiskAnalyzer(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                1_000,
                2_000
        );
        var repeatContext = new RepeatAnalysisContext(
                true, 7, 3, 3, true,
                "OPEN_WAITING_FOR_TEACHER", 2, 1, 3, 3, 30, false, List.of()
        );

        var result = analyzer.analyze(
                "[이름]을 신고하겠습니다.",
                List.of(new RiskTagDetectionResult(
                        RiskTagCode.LEGAL,
                        RiskTagState.BOUNDARY,
                        null,
                        List.of(),
                        "법적 대응 문맥 확인 필요"
                )),
                repeatContext
        );

        assertThat(requestBody.get())
                .contains("[이름]을 신고하겠습니다.")
                .contains("\"repeatThresholdMet\":true")
                .doesNotContain("\"severity\"", "\"repeatCount\"");
        assertThat(result.tags()).hasSize(RiskTagCode.values().length);
        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == RiskTagCode.PROFANITY)
                .singleElement()
                .satisfies(tag -> {
                    assertThat(tag.severity()).isEqualTo(2);
                    assertThat(tag.repeatCount()).isEqualTo(5);
                });
        assertThat(result.safetyFlags().credibleThreat()).isFalse();
        assertThat(result.safetyFlags().studentSafetyConcern()).isNull();
    }

    @Test
    void rejectsStringSafetyFlagInsteadOfBoolean() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ai/analyze", exchange -> {
            byte[] response = extendedResponse()
                    .replace("\"credibleThreat\":false", "\"credibleThreat\":\"false\"")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var analyzer = new PythonLlmRiskAnalyzer(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                1_000,
                2_000
        );

        assertThatThrownBy(() -> analyzer.analyze(
                "마스킹된 민원",
                List.of(),
                new RepeatAnalysisContext(
                        false, 7, 3, 1, false,
                        "DRAFT_WAITING_FOR_NONE", 0, 0, 0, 0, 0, false, List.of()
                )
        )).isInstanceOf(com.teacherhub.ai.exception.AiResponseInvalidException.class);
    }

    private String extendedResponse() {
        String tags = Stream.of(RiskTagCode.values())
                .map(code -> code == RiskTagCode.PROFANITY
                        ? """
                          {"code":"PROFANITY","state":"PRESENT","confidence":0.95,
                           "severity":2,"repeatCount":5,
                           "evidenceSpans":[{"text":"젠장","start":null,"end":null}],
                           "reason":"거친 표현이 반복됨"}
                          """
                        : """
                          {"code":"%s","state":"ABSENT","confidence":0.9,
                           "severity":0,"repeatCount":0,"evidenceSpans":[],"reason":null}
                          """.formatted(code.name()))
                .collect(Collectors.joining(","));
        return """
                {"tags":[%s],
                 "metadata":{"modelName":"claude-sonnet-4-6","temperature":0.0},
                 "safetyFlags":{"credibleThreat":false,"studentSafetyConcern":null,
                                "privacyExposure":false}}
                """.formatted(tags);
    }
}
