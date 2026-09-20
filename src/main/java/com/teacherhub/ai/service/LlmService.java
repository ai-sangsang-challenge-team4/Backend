package com.teacherhub.ai.service;

import com.teacherhub.ai.client.LlmClient;
import com.teacherhub.ai.dto.LlmRequest;
import com.teacherhub.ai.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final LlmClient llmClient;

    public LlmResponse generate(String content) {

        LlmRequest request = new LlmRequest(content);

        return llmClient.generate(request);
    }
}