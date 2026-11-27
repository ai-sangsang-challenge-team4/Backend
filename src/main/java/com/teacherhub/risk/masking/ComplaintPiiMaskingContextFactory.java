package com.teacherhub.risk.masking;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.school.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintPiiMaskingContextFactory {

    private final StudentClassRepository studentClassRepository;

    public PiiMaskingContext create(Complaint complaint) {
        PiiMaskingContext baseContext = PiiMaskingContext.from(complaint);
        if (complaint == null || complaint.getStudent() == null || complaint.getStudent().getId() == null) {
            return baseContext;
        }

        return studentClassRepository
                .findTopByStudent_IdOrderBySchoolClass_AcademicYearDescIdDesc(complaint.getStudent().getId())
                .map(studentClass -> classmates(
                        studentClass.getSchoolClass().getId(),
                        complaint.getStudent().getId()
                ))
                .map(baseContext::withThirdPartyStudentNames)
                .orElse(baseContext);
    }

    private List<String> classmates(Long classId, Long complaintStudentId) {
        return studentClassRepository.findBySchoolClass_Id(classId).stream()
                .map(studentClass -> studentClass.getStudent())
                .filter(Objects::nonNull)
                .filter(student -> !Objects.equals(student.getId(), complaintStudentId))
                .map(student -> student.getName())
                .toList();
    }
}
