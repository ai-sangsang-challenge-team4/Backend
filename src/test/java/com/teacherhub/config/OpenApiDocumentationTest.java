package com.teacherhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesFrontendOrientedOperationsAndCommonErrorSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TeacherHub API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.message").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.timestamp").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.path").exists())
                .andExpect(jsonPath("$.paths['/complaints'].post.summary").value("민원 초안 생성"))
                .andExpect(jsonPath("$.paths['/complaints'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/complaints'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/teacher/complaints/{complaintId}/responses'].post.summary")
                        .value("교사 답변 최종 제출"))
                .andExpect(jsonPath("$.paths['/admin/escalations/{escalationId}/complete'].post.responses['409']")
                        .exists());
    }
}
