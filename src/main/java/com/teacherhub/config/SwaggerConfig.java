package com.teacherhub.config;

import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springdoc.core.customizers.OperationCustomizer;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "TeacherHub API",
                version = "v1",
                description = "학부모 민원 작성, 교사 대응, 위험 분석 및 관리자 공유를 위한 REST API"
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class SwaggerConfig {

    private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";

    @Bean
    public OperationCustomizer commonErrorResponseCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }

            addIfMissing(responses, "400", errorResponse(
                    "요청 검증·형식 오류",
                    "VALIDATION_FAILED",
                    "요청 값이 올바르지 않습니다."
            ));
            addIfMissing(responses, "401", errorResponse(
                    "인증 필요 또는 만료된 토큰",
                    "UNAUTHORIZED",
                    "로그인이 필요합니다."
            ));
            addIfMissing(responses, "403", errorResponse(
                    "역할·자원 접근 권한 없음",
                    "COMPLAINT_ACCESS_DENIED",
                    "해당 민원에 접근할 권한이 없습니다."
            ));
            addIfMissing(responses, "404", errorResponse(
                    "사용자·민원·메시지·분석 결과 등을 찾을 수 없음",
                    "COMPLAINT_NOT_FOUND",
                    "민원을 찾을 수 없습니다."
            ));
            addIfMissing(responses, "409", errorResponse(
                    "상태 전이·중복 요청·동시 수정 충돌",
                    "RESPONSE_NOT_EDITABLE",
                    "응답을 현재 상태에서 처리할 수 없습니다."
            ));
            addIfMissing(responses, "500", errorResponse(
                    "예상하지 못한 서버 오류",
                    "INTERNAL_SERVER_ERROR",
                    "서버 내부 오류가 발생했습니다."
            ));
            return operation;
        };
    }

    private void addIfMissing(ApiResponses responses, String status, ApiResponse response) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(status, response);
        }
    }

    private ApiResponse errorResponse(String description, String code, String message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", code);
        value.put("message", message);
        value.put("timestamp", "2026-10-29T12:00:00");
        value.put("path", "/api/resource");

        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref(ERROR_SCHEMA))
                .addExamples("example", new Example().value(value));
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", mediaType));
    }
}
