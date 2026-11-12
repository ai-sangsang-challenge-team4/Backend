package com.teacherhub.ai.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.teacherhub.ai.exception.AiRequestPreparationException;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.ai.exception.AiServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 외부 AI 서버의 공통 HTTP·JSON 신뢰 경계입니다. */
@Component
public class AiServerClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final Duration requestTimeout;

    public AiServerClient(
            @Value("${ai.server.url:http://localhost:8000}") String aiServerUrl,
            @Value("${ai.server.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${ai.server.request-timeout-ms:60000}") long requestTimeoutMs
    ) {
        if (connectTimeoutMs < 1 || requestTimeoutMs < 1) {
            throw new IllegalArgumentException("AI 서버 제한 시간은 1ms 이상이어야 합니다.");
        }
        this.baseUri = normalizeBaseUri(aiServerUrl);
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.objectMapper = strictObjectMapper();
    }

    public <T> T post(
            String path,
            Object requestBody,
            Class<T> responseType,
            String capabilityName
    ) {
        HttpRequest request = prepareRequest(path, requestBody, capabilityName);
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throwForStatus(response.statusCode(), capabilityName);
            }
            try {
                return objectMapper.readValue(response.body(), responseType);
            } catch (Exception exception) {
                throw new AiResponseInvalidException(
                        capabilityName + " 응답을 해석할 수 없습니다.",
                        exception
                );
            }
        } catch (AiResponseInvalidException | AiServiceUnavailableException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiServiceUnavailableException(
                    capabilityName + " 서비스 호출이 중단되었습니다.",
                    exception
            );
        } catch (HttpTimeoutException exception) {
            throw new AiServiceUnavailableException(
                    capabilityName + " 서비스 응답 시간이 초과되었습니다.",
                    exception
            );
        } catch (IOException exception) {
            throw new AiServiceUnavailableException(
                    capabilityName + " 서비스에 연결할 수 없습니다.",
                    exception
            );
        }
    }

    private HttpRequest prepareRequest(
            String path,
            Object requestBody,
            String capabilityName
    ) {
        try {
            String requestJson = objectMapper.writeValueAsString(requestBody);
            return HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(resolve(path))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestJson,
                            StandardCharsets.UTF_8
                    ))
                    .build();
        } catch (Exception exception) {
            throw new AiRequestPreparationException(
                    capabilityName + " 요청을 준비할 수 없습니다.",
                    exception
            );
        }
    }

    private URI resolve(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("AI 서버 경로는 '/'로 시작해야 합니다.");
        }
        return baseUri.resolve(path.substring(1));
    }

    private void throwForStatus(int statusCode, String capabilityName) {
        if (statusCode == 429 || statusCode >= 500) {
            throw new AiServiceUnavailableException(
                    capabilityName + " 서비스를 일시적으로 사용할 수 없습니다."
            );
        }
        throw new AiRequestPreparationException(
                capabilityName + " 서비스가 요청을 거부했습니다."
        );
    }

    private static URI normalizeBaseUri(String value) {
        try {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("AI 서버 주소가 필요합니다.");
            }
            URI uri = URI.create(normalized.endsWith("/") ? normalized : normalized + "/");
            if (!("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("AI 서버 주소는 HTTP(S) URL이어야 합니다.");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AI 서버 주소가 올바르지 않습니다.", exception);
        }
    }

    private static ObjectMapper strictObjectMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        mapper.coercionConfigFor(LogicalType.Boolean)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Float)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        return mapper;
    }
}
