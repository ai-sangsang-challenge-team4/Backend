package com.teacherhub.ai.service;

import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.ai.client.LlmClient;
import com.teacherhub.ai.dto.LlmRequest;
import com.teacherhub.ai.dto.LlmResponse;
import com.teacherhub.common.exception.InternalSystemException;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.masking.PiiMaskingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final LlmClient llmClient;
    private final PiiMaskingService piiMaskingService;

    public LlmResponse generate(String content) {
        String maskedContent;
        try {
            maskedContent = piiMaskingService.mask(content);
            if (maskedContent == null) {
                throw new IllegalStateException("마스킹 결과가 비어 있습니다.");
            }
        } catch (RuntimeException exception) {
            throw new InternalSystemException("개인정보 보호 처리 중 오류가 발생했습니다.", exception);
        }
        LlmRequest request = new LlmRequest(maskedContent);

        LlmResponse response = llmClient.generate(request);
        validate(response);
        return response;
    }

    private void validate(LlmResponse response) {
        if (response == null
                || response.summary() == null
                || response.summary().isBlank()
                || response.summary().length() > 1_500
                || response.riskTags() == null
                || response.riskTags().stream().anyMatch(tag -> tag == null || tag.isBlank())
                || new HashSet<>(response.riskTags()).size() != response.riskTags().size()
                || response.riskTags().stream().anyMatch(this::isUnknownTag)) {
            throw new AiResponseInvalidException("AI 응답 형식이 올바르지 않습니다.");
        }
    }

    private boolean isUnknownTag(String tag) {
        try {
            RiskTagCode.valueOf(tag);
            return false;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }
}
