package com.teacherhub.user.consent.repository;

import com.teacherhub.user.consent.entity.UserPolicyConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPolicyConsentRepository extends JpaRepository<UserPolicyConsent, Long> {

    Optional<UserPolicyConsent> findByUserIdAndPolicyVersion(Long userId, String policyVersion);
}
