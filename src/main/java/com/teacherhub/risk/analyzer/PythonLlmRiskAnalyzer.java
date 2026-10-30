package com.teacherhub.risk.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
@Primary
public class PythonLlmRiskAnalyzer implements LlmRiskAnalyzer {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LlmRiskAnalysisResult analyze(String maskedContent) {

        System.out.println("[AI 요청] " + maskedContent);

        try {
            String escapedContent = maskedContent
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");

            String requestBody = "{\"complaint\":\""
                    + escapedContent
                    + "\"}";

            System.out.println("[AI 요청 Body] " + requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(URI.create("http://localhost:8000/api/v1/ai/analyze"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestBody,
                            StandardCharsets.UTF_8
                    ))
                    .build();

            System.out.println("[AI HTTP Version] HTTP/1.1");
            System.out.println("[AI Content-Type] application/json; charset=UTF-8");

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            System.out.println("[AI 응답 상태] " + response.statusCode());
            System.out.println("[AI 응답 Body] " + response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "AI 서버 오류: HTTP "
                                + response.statusCode()
                                + " / "
                                + response.body()
                );
            }

            return objectMapper.readValue(
                    response.body(),
                    LlmRiskAnalysisResult.class
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Python AI 서버 호출 실패",
                    e
            );
        }
    }
}