package com.teacherhub.complaint.template.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "response_template_versions", uniqueConstraints =
        @UniqueConstraint(name = "uk_response_template_version", columnNames = {"template_id", "version_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResponseTemplateVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ResponseTemplate template;

    @Column(name = "version_number", nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contentTemplate;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ResponseTemplateVersion(ResponseTemplate template, int version, String contentTemplate, boolean active) {
        this.template = template;
        this.version = version;
        this.contentTemplate = contentTemplate;
        this.active = active;
    }
}
