package com.teacherhub.common.exception;

import com.teacherhub.security.ApiAccessDeniedHandler;
import com.teacherhub.security.ApiAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExceptionHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ApiErrorResponseWriter writer = new ApiErrorResponseWriter(objectMapper);

    @Test
    void unauthenticatedRequestUsesCommonErrorResponse() throws Exception {
        var request = new MockHttpServletRequest("GET", "/teacher/complaints");
        var response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint(writer).commence(
                request,
                response,
                new InsufficientAuthenticationException("missing token")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).isEqualTo("인증이 필요합니다.");
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("path").asText()).isEqualTo("/teacher/complaints");
        assertThat(body.has("status")).isFalse();
    }

    @Test
    void forbiddenRequestUsesCommonErrorResponse() throws Exception {
        var request = new MockHttpServletRequest("GET", "/admin/escalations");
        var response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler(writer).handle(
                request,
                response,
                new AccessDeniedException("wrong role")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("message").asText()).isEqualTo("접근 권한이 없습니다.");
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("path").asText()).isEqualTo("/admin/escalations");
        assertThat(body.has("status")).isFalse();
    }
}
