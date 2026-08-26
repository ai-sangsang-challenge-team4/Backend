package com.teacherhub.ai;

import com.teacherhub.ai.dto.AiReviewResult;

public interface ComplaintAiAnalyzer {
    AiReviewResult analyze(String content);
}
