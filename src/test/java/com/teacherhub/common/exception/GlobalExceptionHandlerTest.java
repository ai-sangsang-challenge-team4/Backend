package com.teacherhub.common.exception;

import com.teacherhub.ai.exception.AiRequestPreparationException;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.ai.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void doesNotExposeIllegalArgumentExceptionMessage() {
        var request = new MockHttpServletRequest("POST", "/api/example");

        var response = handler.handleIllegalArgument(
                new IllegalArgumentException("internal value: secret"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("요청 값이 올바르지 않습니다.");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    @Test
    void doesNotExposeIllegalStateExceptionMessage() {
        var request = new MockHttpServletRequest("POST", "/api/example");

        var response = handler.handleIllegalState(
                new IllegalStateException("database state: secret"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_STATE");
        assertThat(response.getBody().message()).isEqualTo("요청을 현재 상태에서 처리할 수 없습니다.");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    @Test
    void distinguishesInvalidAiOutputFromUnavailableAiAndInternalFailures() {
        var request = new MockHttpServletRequest("POST", "/complaints/42/risk-analysis");

        var invalid = handler.handleAiResponseInvalid(
                new AiResponseInvalidException("secret AI body"),
                request
        );
        var unavailable = handler.handleAiServiceUnavailable(
                new AiServiceUnavailableException("provider secret"),
                request
        );
        var integration = handler.handleAiRequestPreparation(
                new AiRequestPreparationException("configuration secret"),
                request
        );
        var internal = handler.handleInternalSystem(
                new InternalSystemException("database secret"),
                request
        );

        assertThat(invalid.getStatusCode().value()).isEqualTo(502);
        assertThat(invalid.getBody()).isNotNull();
        assertThat(invalid.getBody().code()).isEqualTo("AI_RESPONSE_INVALID");
        assertThat(invalid.getBody().message()).doesNotContain("secret");

        assertThat(unavailable.getStatusCode().value()).isEqualTo(503);
        assertThat(unavailable.getBody()).isNotNull();
        assertThat(unavailable.getBody().code()).isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(unavailable.getBody().message()).doesNotContain("secret");

        assertThat(integration.getStatusCode().value()).isEqualTo(500);
        assertThat(integration.getBody()).isNotNull();
        assertThat(integration.getBody().code()).isEqualTo("AI_INTEGRATION_ERROR");

        assertThat(internal.getStatusCode().value()).isEqualTo(500);
        assertThat(internal.getBody()).isNotNull();
        assertThat(internal.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
