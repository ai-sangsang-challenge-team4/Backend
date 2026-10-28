package com.teacherhub.common.exception;

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
}
