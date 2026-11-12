package com.teacherhub.risk.analyzer;

import com.sun.net.httpserver.HttpServer;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.ai.client.AiServerClient;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonComplaintRevisionGeneratorTest {

    private HttpServer server;

    private final PythonComplaintRevisionGenerator generator =
            new PythonComplaintRevisionGenerator(
                    new AiServerClient("http://localhost", 1, 1)
            );

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void exchangesRevisionContractOverHttp() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ai/revision", exchange -> {
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));
            byte[] response = """
                    {"finalRevision":{"reason":"위협 표현 완화","content":"확인을 요청드립니다."},
                     "tagSuggestions":[{"code":"THREAT","originalExpression":"찾아가겠다",
                     "suggestedExpression":"확인을 요청드립니다","reason":"위협 가능성 완화"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var httpGenerator = new PythonComplaintRevisionGenerator(new AiServerClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                1_000,
                2_000
        ));
        var riskEvaluation = new RiskEvaluation(
                42L,
                List.of(new FinalRiskTagResponse(
                        RiskTagCode.THREAT,
                        RiskTagState.PRESENT,
                        RiskTagState.PRESENT,
                        RiskTagState.PRESENT,
                        0.9,
                        2,
                        1,
                        List.of(EvidenceSpan.unlocated("찾아가겠다")),
                        "위협 문맥"
                )),
                45,
                RiskLevel.HIGH
        );

        var result = httpGenerator.generate("[이름]에게 찾아가겠다", riskEvaluation);

        assertThat(requestBody.get())
                .contains("\"complaint\":\"[이름]에게 찾아가겠다\"")
                .contains("\"code\":\"THREAT\"")
                .contains("\"riskScore\":45")
                .contains("\"riskLevel\":\"HIGH\"");
        assertThat(result.finalRevision().content()).isEqualTo("확인을 요청드립니다.");
        assertThat(result.tagSuggestions()).singleElement()
                .satisfies(suggestion -> assertThat(suggestion.code())
                        .isEqualTo(RiskTagCode.THREAT));
    }

    @Test
    void parsesAllowedRevisionTagCode() {
        assertThat(generator.parseRiskTagCode("THREAT")).isEqualTo(RiskTagCode.THREAT);
    }

    @Test
    void rejectsUnknownOrMissingRevisionTagCodeAsInvalidAiOutput() {
        assertThatThrownBy(() -> generator.parseRiskTagCode("NOT_ALLOWED"))
                .isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> generator.parseRiskTagCode(null))
                .isInstanceOf(AiResponseInvalidException.class);
    }
}
