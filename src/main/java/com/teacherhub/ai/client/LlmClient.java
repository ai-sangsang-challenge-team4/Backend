package com.teacherhub.ai.client;

import com.teacherhub.ai.dto.LlmRequest;
import com.teacherhub.ai.dto.LlmResponse;

public interface LlmClient {

    LlmResponse generate(LlmRequest request);
}