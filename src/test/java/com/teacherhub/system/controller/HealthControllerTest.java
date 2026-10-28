package com.teacherhub.system.controller;

import com.teacherhub.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    @Test
    void returnsUpWithoutExternalDependencies() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void preservesNotAcceptableStatusAndUsesCommonErrorResponse() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new HealthController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/health").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"))
                .andExpect(jsonPath("$.message").value("요청한 응답 형식을 제공할 수 없습니다."))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/health"));
    }
}
