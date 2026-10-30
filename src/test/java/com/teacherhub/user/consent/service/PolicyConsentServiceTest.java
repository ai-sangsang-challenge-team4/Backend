package com.teacherhub.user.consent.service;

import com.teacherhub.user.consent.entity.UserPolicyConsent;
import com.teacherhub.user.consent.dto.PolicyConsentRequest;
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
        when(user.getId()).thenReturn(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(consentRepository.findByUserIdAndPolicyVersion(10L, "2026-10-29-v1"))
                .thenReturn(Optional.empty());
        when(consentRepository.saveAndFlush(any(UserPolicyConsent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.saveCurrent(10L, new PolicyConsentRequest(true, false));

        assertThat(response.policyVersion()).isEqualTo("2026-10-29-v1");
        assertThat(response.agreedAt()).isNotNull();
        assertThat(response.requiredAgreed()).isTrue();
        assertThat(response.optionalAgreed()).isFalse();
        verify(consentRepository).saveAndFlush(any(UserPolicyConsent.class));
    }

    @Test
    void samePolicyVersionIsIdempotent() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        UserPolicyConsent existing = new UserPolicyConsent(user, "2026-10-29-v1", true, false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(consentRepository.findByUserIdAndPolicyVersion(10L, "2026-10-29-v1"))
                .thenReturn(Optional.of(existing));
        when(consentRepository.saveAndFlush(existing)).thenReturn(existing);

        var response = service.saveCurrent(10L, new PolicyConsentRequest(true, true));

        assertThat(response.policyVersion()).isEqualTo("2026-10-29-v1");
        assertThat(response.optionalAgreed()).isTrue();
        verify(consentRepository).saveAndFlush(existing);
    }

    @Test
    void returnsNotAgreedStatusWhenCurrentVersionHasNoRecord() {
        User user = mock(User.class);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(consentRepository.findByUserIdAndPolicyVersion(10L, "2026-10-29-v1"))
                .thenReturn(Optional.empty());

        var response = service.findCurrent(10L);

        assertThat(response.consentId()).isNull();
        assertThat(response.requiredAgreed()).isFalse();
        assertThat(response.optionalAgreed()).isFalse();
        verify(consentRepository, never()).saveAndFlush(any());
    }
}
