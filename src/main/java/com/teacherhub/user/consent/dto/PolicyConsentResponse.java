package com.teacherhub.user.consent.dto;

import com.teacherhub.user.consent.entity.UserPolicyConsent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "정책 동의 저장 결과")
public record PolicyConsentResponse(
        Long consentId,
        String policyVersion,
        LocalDateTime agreedAt,
        boolean requiredAgreed,
        boolean optionalAgreed
) {
    public static PolicyConsentResponse from(UserPolicyConsent consent) {
        return new PolicyConsentResponse(
                consent.getId(),
                consent.getPolicyVersion(),
                consent.getAgreedAt(),
                consent.isRequiredAgreed(),
                consent.isOptionalAgreed()
        );
    }

    public static PolicyConsentResponse notAgreed(String policyVersion) {
        return new PolicyConsentResponse(null, policyVersion, null, false, false);
    }
}
