package com.teacherhub.complaint.template.entity;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "response_template_adoptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResponseTemplateAdoption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adopted_by", nullable = false)
    private User adoptedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id", nullable = false)
    private ResponseTemplateVersion templateVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String renderedContent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String finalContent;

    @Column(nullable = false, updatable = false)
    private LocalDateTime adoptedAt;

    public ResponseTemplateAdoption(
            Complaint complaint,
            User adoptedBy,
            ResponseTemplateVersion templateVersion,
            String renderedContent,
            String finalContent
    ) {
        this.complaint = complaint;
        this.adoptedBy = adoptedBy;
        this.templateVersion = templateVersion;
        this.renderedContent = renderedContent;
        this.finalContent = finalContent;
        this.adoptedAt = LocalDateTime.now();
    }
}
