package com.teacherhub.ai.client;

import com.teacherhub.ai.dto.LlmRequest;
import com.teacherhub.ai.dto.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockLlmClient implements LlmClient {

    @Override
    public LlmResponse generate(LlmRequest request) {

        return new LlmResponse(
                "현재는 Mock 응답입니다: " + request.content(),
                List.of("LEGAL")
        );
    }
}