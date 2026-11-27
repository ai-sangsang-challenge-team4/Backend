package com.teacherhub.risk.masking;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.Student;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplaintPiiMaskingContextFactoryTest {

    @Test
    void addsStudentsFromTheComplaintStudentsLatestClassAsThirdParties() {
        StudentClassRepository studentClasses = mock(StudentClassRepository.class);
        Complaint complaint = mock(Complaint.class);
        Student complaintStudent = student(10L, "김서윤");
        Student peer = student(11L, "박민수");
        Student otherPeer = student(12L, "이서준");
        SchoolClass schoolClass = mock(SchoolClass.class);
        StudentClass currentAssignment = assignment(complaintStudent, schoolClass);
        StudentClass peerAssignment = assignment(peer, schoolClass);
        StudentClass otherPeerAssignment = assignment(otherPeer, schoolClass);

        when(complaint.getStudent()).thenReturn(complaintStudent);
        when(schoolClass.getId()).thenReturn(20L);
        when(studentClasses.findTopByStudent_IdOrderBySchoolClass_AcademicYearDescIdDesc(10L))
                .thenReturn(Optional.of(currentAssignment));
        when(studentClasses.findBySchoolClass_Id(20L)).thenReturn(List.of(
                currentAssignment,
                peerAssignment,
                otherPeerAssignment
        ));

        PiiMaskingContext context = new ComplaintPiiMaskingContextFactory(studentClasses).create(complaint);

        assertThat(context.studentNames()).containsExactly("김서윤");
        assertThat(context.thirdPartyStudentNames()).containsExactly("박민수", "이서준");
    }

    private Student student(Long id, String name) {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(id);
        when(student.getName()).thenReturn(name);
        return student;
    }

    private StudentClass assignment(Student student, SchoolClass schoolClass) {
        StudentClass assignment = mock(StudentClass.class);
        when(assignment.getStudent()).thenReturn(student);
        when(assignment.getSchoolClass()).thenReturn(schoolClass);
        return assignment;
    }
}
