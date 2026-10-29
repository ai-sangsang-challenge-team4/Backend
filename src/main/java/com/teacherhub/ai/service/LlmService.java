package com.teacherhub.ai.service;

import com.teacherhub.ai.client.LlmClient;
import com.teacherhub.ai.dto.LlmRequest;
import com.teacherhub.ai.dto.LlmResponse;
import com.teacherhub.risk.masking.PiiMaskingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final LlmClient llmClient;
    private final PiiMaskingService piiMaskingService;

    public LlmResponse generate(String content) {
        String maskedContent = piiMaskingService.mask(content);
        LlmRequest request = new LlmRequest(maskedContent);

        return llmClient.generate(request);
    }
}
