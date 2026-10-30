package com.teacherhub.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    void conversationMessageSchemasHaveDistinctNames() {
        var parentSchema = com.teacherhub.complaint.parent.dto.message
                .ComplaintConversationResponse.Message.class
                .getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        var teacherSchema = com.teacherhub.complaint.teacher.dto.query
                .TeacherConversationResponse.Message.class
                .getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);

        assertThat(parentSchema.name()).isEqualTo("ParentConversationMessage");
        assertThat(teacherSchema.name()).isEqualTo("TeacherConversationMessage");
    }

    @Test
    void addsCustomExceptionStatusResponsesAndPreservesEndpointDocumentation() {
        SwaggerConfig config = new SwaggerConfig();
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("성공"))
                .addApiResponse("404", new ApiResponse().description("해당 메시지를 찾을 수 없음")));

        config.commonErrorResponseCustomizer().customize(operation, null);

        assertThat(operation.getResponses()).containsKeys(
                "200", "400", "401", "403", "404", "409", "500"
        );
        assertThat(operation.getResponses().get("404").getDescription())
                .isEqualTo("해당 메시지를 찾을 수 없음");
        assertThat(operation.getResponses().get("409").getDescription())
                .contains("상태 전이", "중복 요청", "동시 수정");
        assertThat(operation.getResponses().get("409").getContent().get("application/json")
                .getExamples().get("example").getValue().toString())
                .contains("RESPONSE_NOT_EDITABLE");
    }
}
