package com.teacherhub.user.consent.dto;

import com.teacherhub.user.consent.entity.UserPolicyConsent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "정책 동의 저장 결과")
public record PolicyConsentResponse(
        Long consentId,
        String policyVersion,
        LocalDateTime agreedAt,
        boolean allAgreed
) {
    public static PolicyConsentResponse from(UserPolicyConsent consent) {
        return new PolicyConsentResponse(
                consent.getId(),
                consent.getPolicyVersion(),
                consent.getAgreedAt(),
                true
        );
    }
}
