package com.teacherhub.user.consent.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.user.consent.dto.PolicyConsentResponse;
import com.teacherhub.user.consent.dto.PolicyConsentRequest;
import com.teacherhub.user.consent.entity.UserPolicyConsent;
import com.teacherhub.user.consent.repository.UserPolicyConsentRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyConsentService {

    private final UserPolicyConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final String currentPolicyVersion;

    // 정책
    public PolicyConsentService(
            UserPolicyConsentRepository consentRepository,
            UserRepository userRepository,
            @Value("${teacherhub.policy.version:2026-10-29-v1}") String currentPolicyVersion
    ) {
        this.consentRepository = consentRepository;
        this.userRepository = userRepository;
        this.currentPolicyVersion = currentPolicyVersion;
    }

    @Transactional
    public PolicyConsentResponse findCurrent(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
        return consentRepository.findByUserIdAndPolicyVersion(userId, currentPolicyVersion)
                .map(PolicyConsentResponse::from)
                .orElseGet(() -> PolicyConsentResponse.notAgreed(currentPolicyVersion));
    }

    @Transactional
    public PolicyConsentResponse saveCurrent(Long userId, PolicyConsentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));

        return save(user, request.requiredAgreed(), request.optionalAgreed());
    }

    @Transactional
    public PolicyConsentResponse recordSignupConsent(
            User user,
            boolean requiredAgreed,
            boolean optionalAgreed
    ) {
        return save(user, requiredAgreed, optionalAgreed);
    }

    private PolicyConsentResponse save(User user, boolean requiredAgreed, boolean optionalAgreed) {
        UserPolicyConsent consent = consentRepository
                .findByUserIdAndPolicyVersion(user.getId(), currentPolicyVersion)
                .orElseGet(() -> new UserPolicyConsent(
                        user,
                        currentPolicyVersion,
                        requiredAgreed,
                        optionalAgreed
                ));
        consent.updateOptionalAgreement(optionalAgreed);
        return PolicyConsentResponse.from(consentRepository.saveAndFlush(consent));
    }
}
