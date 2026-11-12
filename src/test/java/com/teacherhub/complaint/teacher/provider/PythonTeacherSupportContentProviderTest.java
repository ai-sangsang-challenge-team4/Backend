package com.teacherhub.complaint.teacher.provider;

import com.sun.net.httpserver.HttpServer;
import com.teacherhub.ai.client.AiServerClient;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.ai.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonTeacherSupportContentProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void exchangesSummaryAndResponseDraftContractsOverHttp() throws IOException {
        AtomicReference<String> summaryRequest = new AtomicReference<>();
        AtomicReference<String> draftRequest = new AtomicReference<>();
        startServer();
        server.createContext("/api/v1/ai/summary", exchange -> {
            summaryRequest.set(readRequest(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, """
                    {"summary":"완충된 민원 요약","modelName":"claude-sonnet-4-6"}
                    """);
        });
        server.createContext("/api/v1/ai/response-draft", exchange -> {
            draftRequest.set(readRequest(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, """
                    {"content":"안녕하세요. 확인 후 안내드리겠습니다.",
                     "modelName":"claude-sonnet-4-6"}
                    """);
        });
        server.start();
        var provider = provider(true);

        String summary = provider.createBufferedSummary("[이름] 보호자의 마스킹된 민원");
        var draft = provider.createResponseDraft("PARENT_INQUIRY: [전화번호] 확인 요청");

        assertThat(summary).isEqualTo("완충된 민원 요약");
        assertThat(draft.content()).isEqualTo("안녕하세요. 확인 후 안내드리겠습니다.");
        assertThat(draft.modelName()).isEqualTo("claude-sonnet-4-6");
        assertThat(summaryRequest.get()).isEqualTo("{\"content\":\"[이름] 보호자의 마스킹된 민원\"}");
        assertThat(draftRequest.get()).contains("[전화번호]");
    }

    @Test
    void rejectsUnknownResponseFieldsAtAiTrustBoundary() throws IOException {
        startServer();
        server.createContext("/api/v1/ai/summary", exchange -> respond(
                exchange,
                200,
                """
                {"summary":"요약","modelName":"test-model","unexpected":"value"}
                """
        ));
        server.start();

        assertThatThrownBy(() -> provider(false).createBufferedSummary("마스킹된 민원"))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    @Test
    void mapsAiServerFailureToUnavailableException() throws IOException {
        startServer();
        server.createContext("/api/v1/ai/response-draft", exchange ->
                respond(exchange, 503, "{\"detail\":\"unavailable\"}"));
        server.start();

        assertThatThrownBy(() -> provider(false).createResponseDraft("마스킹된 대화"))
                .isInstanceOf(AiServiceUnavailableException.class);
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private PythonTeacherSupportContentProvider provider(boolean trailingSlash) {
        String suffix = trailingSlash ? "/" : "";
        return new PythonTeacherSupportContentProvider(new AiServerClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + suffix,
                1_000,
                2_000
        ));
    }

    private String readRequest(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void respond(
            com.sun.net.httpserver.HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
