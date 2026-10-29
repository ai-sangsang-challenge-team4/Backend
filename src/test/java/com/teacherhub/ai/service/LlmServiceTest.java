package com.teacherhub.ai.service;

import com.teacherhub.ai.client.LlmClient;
import com.teacherhub.ai.dto.LlmRequest;
import com.teacherhub.risk.masking.PiiMaskingService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmServiceTest {

    @Test
    void sendsOnlyMaskedContentToLlmClient() {
        LlmClient client = mock(LlmClient.class);
        PiiMaskingService masking = mock(PiiMaskingService.class);
        LlmService service = new LlmService(client, masking);
        String original = "연락처는 010-1234-5678입니다.";
        String masked = "연락처는 [전화번호]입니다.";
        when(masking.mask(original)).thenReturn(masked);

        service.generate(original);

        verify(masking).mask(original);
        verify(client).generate(new LlmRequest(masked));
    }
}
