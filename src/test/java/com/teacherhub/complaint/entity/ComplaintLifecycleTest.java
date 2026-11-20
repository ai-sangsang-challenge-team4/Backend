package com.teacherhub.complaint.entity;

import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplaintLifecycleTest {

    @Test
    void separatesTeacherAnswerFromParentClosureAndUsesThreeDayBoundary() {
        Complaint complaint = complaint();
        LocalDateTime answeredAt = LocalDateTime.of(2026, 11, 20, 9, 0);

        complaint.submit();
        complaint.teacherMessageSent(answeredAt);

        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.ANSWERED);
        assertThat(complaint.getWaitingFor()).isEqualTo(WaitingFor.PARENT);
        assertThat(complaint.getAnsweredAt()).isEqualTo(answeredAt);
        assertThat(complaint.isAnsweredNoLaterThan(answeredAt.minusNanos(1))).isFalse();
        assertThat(complaint.isAnsweredNoLaterThan(answeredAt)).isTrue();

        complaint.closeByParent(answeredAt.plusHours(2));

        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.CLOSED);
        assertThat(complaint.getWaitingFor()).isEqualTo(WaitingFor.NONE);
    }

    @Test
    void followUpReopensAnsweredOrClosedComplaintAndClearsClosureTimes() {
        Complaint answered = answeredComplaint();
        ComplaintStatus answeredPrevious = answered.parentMessageSent();

        assertThat(answeredPrevious).isEqualTo(ComplaintStatus.ANSWERED);
        assertThat(answered.getStatus()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(answered.getWaitingFor()).isEqualTo(WaitingFor.TEACHER);
        assertThat(answered.getAnsweredAt()).isNull();

        Complaint closed = answeredComplaint();
        closed.closeAutomatically(LocalDateTime.of(2026, 11, 23, 9, 0));
        ComplaintStatus closedPrevious = closed.parentMessageSent();

        assertThat(closedPrevious).isEqualTo(ComplaintStatus.CLOSED);
        assertThat(closed.getStatus()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(closed.getClosedAt()).isNull();
    }

    @Test
    void cannotCloseBeforeTeacherAnswer() {
        Complaint complaint = complaint();
        complaint.submit();

        assertThatThrownBy(() -> complaint.closeByParent(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("교사 답변 완료 후");
    }

    private Complaint answeredComplaint() {
        Complaint complaint = complaint();
        complaint.submit();
        complaint.teacherMessageSent(LocalDateTime.of(2026, 11, 20, 9, 0));
        return complaint;
    }

    private Complaint complaint() {
        User parentUser = new User("parent@test.com", "password", "학부모", UserRole.PARENT);
        User teacherUser = new User("teacher@test.com", "password", "교사", UserRole.TEACHER);
        return new Complaint(
                new Parent(parentUser),
                new Student("학생", "S-1"),
                new Teacher(teacherUser, "T-1"),
                "문의 제목",
                "문의 내용"
        );
    }
}
