package com.teacherhub.complaint.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_original_view_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplaintOriginalViewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viewed_by", nullable = false)
    private User viewedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime viewedAt;

    public ComplaintOriginalViewLog(Complaint complaint, User viewedBy) {
        this.complaint = complaint;
        this.viewedBy = viewedBy;
        this.viewedAt = LocalDateTime.now();
    }
}
