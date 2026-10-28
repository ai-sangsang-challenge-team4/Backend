package com.teacherhub.auth.service;

import com.teacherhub.auth.dto.SignupOptionResponse;
import com.teacherhub.auth.exception.SignupReferenceNotFoundException;
import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.SchoolRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SignupOptionService {

    private final SchoolRepository schoolRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentClassRepository studentClassRepository;

    @Transactional(readOnly = true)
    public List<SignupOptionResponse> schools(String keyword) {
        var schools = keyword == null || keyword.isBlank()
                ? schoolRepository.findTop20ByOrderByNameAsc()
                : schoolRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(keyword.trim());

        return schools.stream().map(SignupOptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SignupOptionResponse> schoolClasses(
            Long schoolId,
            int academicYear,
            Integer grade
    ) {
        if (!schoolRepository.existsById(schoolId)) {
            throw new SignupReferenceNotFoundException("학교를 찾을 수 없습니다.");
        }

        var classes = grade == null
                ? schoolClassRepository.findBySchool_IdAndAcademicYearOrderByGradeAscClassNoAsc(
                        schoolId,
                        academicYear
                )
                : schoolClassRepository.findBySchool_IdAndAcademicYearAndGradeOrderByClassNoAsc(
                        schoolId,
                        academicYear,
                        grade
                );

        return classes.stream().map(SignupOptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SignupOptionResponse> students(Long schoolClassId) {
        if (!schoolClassRepository.existsById(schoolClassId)) {
            throw new SignupReferenceNotFoundException("학급을 찾을 수 없습니다.");
        }

        return studentClassRepository.findBySchoolClass_IdOrderByStudent_StudentNoAsc(schoolClassId)
                .stream()
                .map(studentClass -> SignupOptionResponse.from(studentClass.getStudent()))
                .toList();
    }
}
