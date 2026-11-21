package com.teacherhub.complaint.mediation.entity;

import com.teacherhub.complaint.entity.Complaint;
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
        name = "mediation_packages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mediation_packages_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uk_mediation_packages_idempotency",
                        columnNames = {"complaint_id", "created_by", "idempotency_key"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediationPackage {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false, updatable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private boolean originalMessagesIncluded;

    @Column(nullable = false, updatable = false)
    private int schemaVersion;

    @Column(nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
    private String snapshotJson;

    @Column(nullable = false, updatable = false, length = 64)
    private String checksumSha256;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MediationPackage(
            String publicId,
            Complaint complaint,
            User createdBy,
            String idempotencyKey,
            boolean originalMessagesIncluded,
            String snapshotJson,
            String checksumSha256,
            LocalDateTime createdAt
    ) {
        this.publicId = publicId;
        this.complaint = complaint;
        this.createdBy = createdBy;
        this.idempotencyKey = idempotencyKey;
        this.originalMessagesIncluded = originalMessagesIncluded;
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.snapshotJson = snapshotJson;
        this.checksumSha256 = checksumSha256;
        this.createdAt = createdAt;
    }
}
