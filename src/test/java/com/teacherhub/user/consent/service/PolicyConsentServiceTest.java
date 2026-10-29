package com.teacherhub.user.consent.service;

import com.teacherhub.user.consent.entity.UserPolicyConsent;
import com.teacherhub.user.consent.repository.UserPolicyConsentRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyConsentServiceTest {

    private final UserPolicyConsentRepository consentRepository = mock(UserPolicyConsentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PolicyConsentService service = new PolicyConsentService(
            consentRepository,
            userRepository,
            "2026-10-29-v1"
    );

    @Test
    void recordsCurrentPolicyVersionAndAgreementTime() {
        User user = mock(User.class);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(consentRepository.findByUserIdAndPolicyVersion(10L, "2026-10-29-v1"))
                .thenReturn(Optional.empty());
        when(consentRepository.saveAndFlush(any(UserPolicyConsent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.agreeAll(10L);

        assertThat(response.policyVersion()).isEqualTo("2026-10-29-v1");
        assertThat(response.agreedAt()).isNotNull();
        assertThat(response.allAgreed()).isTrue();
        verify(consentRepository).saveAndFlush(any(UserPolicyConsent.class));
    }

    @Test
    void samePolicyVersionIsIdempotent() {
        User user = mock(User.class);
        UserPolicyConsent existing = new UserPolicyConsent(user, "2026-10-29-v1");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(consentRepository.findByUserIdAndPolicyVersion(10L, "2026-10-29-v1"))
                .thenReturn(Optional.of(existing));

        var response = service.agreeAll(10L);

        assertThat(response.policyVersion()).isEqualTo("2026-10-29-v1");
        verify(consentRepository, never()).saveAndFlush(any());
    }
}
