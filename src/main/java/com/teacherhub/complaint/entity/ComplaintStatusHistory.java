package com.teacherhub.complaint.entity;

import com.teacherhub.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplaintStatusHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Enumerated(EnumType.STRING)
    private ComplaintStatus previousStatus;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ComplaintStatus newStatus;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    public ComplaintStatusHistory(Complaint complaint, User changedBy,
                                  ComplaintStatus previousStatus, ComplaintStatus newStatus) {
        this.complaint = complaint;
        this.changedBy = changedBy;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = LocalDateTime.now();
    }
}
