package com.teacherhub.complaint.mediation.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.history.ComplaintHistoryService;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent;
import com.teacherhub.complaint.mediation.entity.MediationPackage;
import com.teacherhub.complaint.mediation.exception.MediationPackageConflictException;
import com.teacherhub.complaint.mediation.repository.MediationPackageRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.risk.masking.PiiMaskingContext;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediationPackageServiceTest {

    @Mock ComplaintRepository complaintRepository;
    @Mock ComplaintMessageRepository messageRepository;
    @Mock ComplaintEventRepository eventRepository;
    @Mock RiskAnalysisRepository riskAnalysisRepository;
    @Mock ComplaintRiskTagRepository riskTagRepository;
    @Mock RiskReviewRepository riskReviewRepository;
    @Mock MediationPackageRepository packageRepository;
    @Mock UserRepository userRepository;
    @Mock ComplaintAccessPolicy complaintAccessPolicy;
    @Mock ComplaintHistoryService complaintHistoryService;
    @Mock PiiMaskingService piiMaskingService;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private MediationPackageService service;
    private User teacher;
    private Complaint complaint;
    private ComplaintMessage message;
    private User parent;

    @BeforeEach
    void setUp() {
        service = new MediationPackageService(
                complaintRepository,
                messageRepository,
                eventRepository,
                riskAnalysisRepository,
                riskTagRepository,
                riskReviewRepository,
                packageRepository,
                userRepository,
                complaintAccessPolicy,
                complaintHistoryService,
                piiMaskingService,
                objectMapper
        );
        teacher = org.mockito.Mockito.mock(User.class);
        complaint = org.mockito.Mockito.mock(Complaint.class);
        message = org.mockito.Mockito.mock(ComplaintMessage.class);
        parent = org.mockito.Mockito.mock(User.class);

        when(userRepository.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaintRepository.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
    }

    private void configureSuccessfulGeneration() {
        when(complaint.getId()).thenReturn(42L);
        when(packageRepository.findByComplaintIdAndCreatedByIdAndIdempotencyKey(
                42L, 7L, "package-42-1"
        )).thenReturn(Optional.empty());
        when(messageRepository.findByComplaintIdAndStatusNotOrderByIdAsc(
                42L, MessageStatus.DRAFT
        )).thenReturn(List.of(message));
        when(message.getId()).thenReturn(101L);
        when(message.getSender()).thenReturn(parent);
        when(parent.getRole()).thenReturn(UserRole.PARENT);
        when(message.getType()).thenReturn(MessageType.PARENT_INQUIRY);
        when(message.getStatus()).thenReturn(MessageStatus.SENT);
        when(message.getContentVersion()).thenReturn(2L);
        when(message.getContent()).thenReturn("학생 이름과 010-1234-5678");
        when(riskAnalysisRepository
                .findByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdAsc(
                        42L, MessageStatus.DRAFT
                )).thenReturn(List.of());
        when(riskReviewRepository.findByRiskAnalysisComplaintIdOrderByReviewedAtAscIdAsc(42L))
                .thenReturn(List.of());
        when(complaintHistoryService.findHistory(42L))
                .thenReturn(new TeacherHistoryResponse(42L, List.of()));
        when(piiMaskingService.mask(any(String.class), any(PiiMaskingContext.class)))
                .thenReturn("[학생명]과 [전화번호]");
        when(packageRepository.saveAndFlush(any(MediationPackage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsImmutableMaskedSnapshotWithoutOriginalByDefault() {
        configureSuccessfulGeneration();

        var response = service.create(7L, 42L, false, " package-42-1 ");

        assertThat(response.originalMessagesIncluded()).isFalse();
        assertThat(response.checksumSha256()).hasSize(64);
        assertThat(response.content().messages()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.maskedContent()).isEqualTo("[학생명]과 [전화번호]");
            assertThat(snapshot.originalContent()).isNull();
        });
        verify(packageRepository).saveAndFlush(any(MediationPackage.class));
        verify(eventRepository).save(any(ComplaintEvent.class));
    }

    @Test
    void includesOriginalOnlyWhenExplicitlyRequestedAndDownloadMatchesStoredSnapshot() {
        configureSuccessfulGeneration();

        var response = service.create(7L, 42L, true, "package-42-1");
        when(complaintRepository.findDetailById(42L)).thenReturn(Optional.of(complaint));
        MediationPackage saved = org.mockito.Mockito.mock(MediationPackage.class);
        String storedJson = write(response.content());
        when(saved.getSnapshotJson()).thenReturn(storedJson);
        when(saved.getChecksumSha256()).thenReturn(response.checksumSha256());
        when(packageRepository.findByPublicIdAndComplaintId(response.packageId(), 42L))
                .thenReturn(Optional.of(saved));

        var download = service.download(7L, 42L, response.packageId());

        assertThat(response.content().messages()).singleElement()
                .extracting(MediationPackageContent.MessageSnapshot::originalContent)
                .isEqualTo("학생 이름과 010-1234-5678");
        assertThat(new String(download.content(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(storedJson);
    }

    @Test
    void rejectsReusingIdempotencyKeyWithDifferentOriginalCondition() {
        MediationPackage existing = new MediationPackage(
                "005b45e1-e144-4b1f-8a40-bba6fb8356df",
                complaint,
                teacher,
                "package-42-1",
                false,
                write(emptyContent()),
                "a".repeat(64),
                LocalDateTime.now()
        );
        when(packageRepository.findByComplaintIdAndCreatedByIdAndIdempotencyKey(
                42L, 7L, "package-42-1"
        )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(7L, 42L, true, "package-42-1"))
                .isInstanceOf(MediationPackageConflictException.class);
        verify(packageRepository, never()).saveAndFlush(any());
    }

    private MediationPackageContent emptyContent() {
        return new MediationPackageContent(
                1,
                "005b45e1-e144-4b1f-8a40-bba6fb8356df",
                LocalDateTime.now(),
                new MediationPackageContent.ComplaintSummary(42L, ComplaintStatus.OPEN),
                false,
                List.of(),
                List.of(),
                new MediationPackageContent.HistorySnapshot(true, List.of(), List.of())
        );
    }

    private String write(MediationPackageContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
