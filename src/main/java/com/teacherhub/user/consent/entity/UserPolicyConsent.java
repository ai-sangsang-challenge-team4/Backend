package com.teacherhub.user.consent.entity;

import com.teacherhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_policy_consents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_policy_consents_user_version",
                columnNames = {"user_id", "policy_version"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPolicyConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime agreedAt;

    @Column(name = "required_agreed", nullable = false)
    private boolean requiredAgreed;

    @Column(name = "optional_agreed", nullable = false)
    private boolean optionalAgreed;

    public UserPolicyConsent(
            User user,
            String policyVersion,
            boolean requiredAgreed,
            boolean optionalAgreed
    ) {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("정책 버전이 필요합니다.");
        }
        if (!requiredAgreed) {
            throw new IllegalArgumentException("필수 정책에 동의해야 합니다.");
        }
        this.user = user;
        this.policyVersion = policyVersion;
        this.agreedAt = LocalDateTime.now();
        this.requiredAgreed = true;
        this.optionalAgreed = optionalAgreed;
    }

    public void updateOptionalAgreement(boolean optionalAgreed) {
        this.optionalAgreed = optionalAgreed;
    }
}
