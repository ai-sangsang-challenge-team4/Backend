package com.teacherhub.complaint.parent.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.exception.ComplaintNotClosableException;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.service.RiskAnalysisService;
import com.teacherhub.risk.service.RiskAnalysisTransactionRunner;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.ParentRepository;
import com.teacherhub.user.repository.ParentStudentRepository;
import com.teacherhub.user.repository.StudentRepository;
import com.teacherhub.user.repository.TeacherRepository;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentComplaintClosureServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 9, 0);

    private final UserRepository users = mock(UserRepository.class);
    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final ComplaintMessageRepository messages = mock(ComplaintMessageRepository.class);
    private final ComplaintEventRepository events = mock(ComplaintEventRepository.class);
    private final ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
    private final ParentComplaintService service = new ParentComplaintService(
            users,
            complaints,
            messages,
            mock(RiskAnalysisService.class),
            events,
            mock(ParentRepository.class),
            mock(StudentRepository.class),
            mock(ParentStudentRepository.class),
            mock(StudentClassRepository.class),
            mock(TeacherRepository.class),
            accessPolicy,
            mock(RiskAnalysisTransactionRunner.class),
            Clock.fixed(Instant.parse("2026-11-23T09:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void parentClosesAnsweredComplaintUnderLock() {
        User parent = mock(User.class);
        Complaint complaint = mock(Complaint.class);
        when(users.findById(7L)).thenReturn(Optional.of(parent));
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getStatus()).thenReturn(
                ComplaintStatus.ANSWERED,
                ComplaintStatus.ANSWERED,
                ComplaintStatus.CLOSED
        );
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.PARENT, WaitingFor.NONE);
        when(messages.findFirstByComplaintIdAndTypeAndStatusNotOrderByIdDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.close(7L, 42L);

        assertThat(response.getStatus()).isEqualTo(ComplaintStatus.CLOSED);
        assertThat(response.getWaitingFor()).isEqualTo(WaitingFor.NONE);
        verify(complaint).closeByParent(NOW);
        verify(events).save(any());
    }

    @Test
    void rejectsCloseBeforeTeacherAnswer() {
        User parent = mock(User.class);
        Complaint complaint = mock(Complaint.class);
        when(users.findById(7L)).thenReturn(Optional.of(parent));
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);

        assertThatThrownBy(() -> service.close(7L, 42L))
                .isInstanceOf(ComplaintNotClosableException.class)
                .hasMessageContaining("교사 답변이 완료된");

        verify(complaint, never()).closeByParent(any());
    }

    @Test
    void rejectsNonOwnerBeforeChangingState() {
        User user = mock(User.class);
        Complaint complaint = mock(Complaint.class);
        when(users.findById(8L)).thenReturn(Optional.of(user));
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        doThrow(new ComplaintAccessDeniedException("해당 민원에 접근할 권한이 없습니다."))
                .when(accessPolicy).validateParentOwner(user, complaint);

        assertThatThrownBy(() -> service.close(8L, 42L))
                .isInstanceOf(ComplaintAccessDeniedException.class);

        verify(complaint, never()).closeByParent(any());
    }
}
