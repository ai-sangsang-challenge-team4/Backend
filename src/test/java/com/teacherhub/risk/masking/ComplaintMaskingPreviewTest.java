package com.teacherhub.risk.masking;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.parent.service.ParentComplaintService;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.dto.request.MaskingPreviewRequest;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplaintMaskingPreviewTest {

    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final ParentComplaintService parents = mock(ParentComplaintService.class);
    private final ComplaintPiiMaskingContextFactory contexts = mock(ComplaintPiiMaskingContextFactory.class);
    private final ComplaintMaskingService service =
            new ComplaintMaskingService(complaints, parents, new PiiMaskingServiceImpl(), contexts);

    @Test
    void masksClassmateGivenNamesInAnUnsavedDraftWithAnEmptyTitle() {
        Parent parent = new Parent(mock(User.class));
        Student student = mock(Student.class);
        Teacher teacher = mock(Teacher.class);
        when(parents.findDraftRecipient(1L, 2L, 3L))
                .thenReturn(new ParentComplaintService.DraftRecipient(parent, student, teacher));
        when(contexts.create(student, teacher)).thenReturn(new PiiMaskingContext(
                List.of("김하늘"), List.of("정미래"), List.of("20261234"), List.of("EMP-10"))
                .withThirdPartyStudentNames(List.of("박가람")));

        var result = service.preview(1L, new MaskingPreviewRequest(null, 2L, 3L, "",
                "하늘이가 가람이에게 연락했습니다. 정미래 EMP-10 20261234 010-1234-5678 [제3자 학생명]"));

        assertThat(result.title()).isEmpty();
        assertThat(result.titleCount()).isZero();
        assertThat(result.content()).isEqualTo(
                "[학생명]이가 [제3자 학생명]이에게 연락했습니다. [교사명] [교직원번호] [학번] [전화번호] [제3자 학생명]");
        assertThat(result.contentCount()).isEqualTo(6);
        verifyNoInteractions(complaints);
    }

    @Test
    void checksTheOwnerBeforeLookingUpAnExistingComplaintsClass() {
        var owner = mock(User.class);
        when(owner.getId()).thenReturn(2L);
        var complaint = mock(Complaint.class);
        when(complaint.getParent()).thenReturn(new Parent(owner));
        when(complaints.findById(42L)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> service.preview(1L, new MaskingPreviewRequest(42L, null, null, "", "가람이")))
                .isInstanceOf(ComplaintAccessDeniedException.class);
        verifyNoInteractions(contexts);
    }
}
