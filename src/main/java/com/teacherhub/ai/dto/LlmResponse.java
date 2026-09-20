package com.teacherhub.ai.dto;

import java.util.List;

public record LlmResponse(
        String summary,
        List<String> riskTags
) {
}