package com.teacherhub.auth.service;

import com.teacherhub.auth.dto.SignupOptionResponse;
import com.teacherhub.auth.exception.SignupReferenceNotFoundException;
import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.SchoolRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Comparator;
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

        return studentClassRepository.findBySchoolClass_Id(schoolClassId)
                .stream()
                .sorted(Comparator.comparing(
                        studentClass -> studentClass.getStudent().getStudentNo(),
                        SignupOptionService::compareStudentNumbers
                ))
                .map(studentClass -> SignupOptionResponse.from(studentClass.getStudent()))
                .toList();
    }

    private static int compareStudentNumbers(String left, String right) {
        boolean leftIsNumber = isUnsignedNumber(left);
        boolean rightIsNumber = isUnsignedNumber(right);

        if (leftIsNumber && rightIsNumber) {
            int numericComparison = new BigInteger(left).compareTo(new BigInteger(right));
            return numericComparison != 0 ? numericComparison : left.compareTo(right);
        }
        if (leftIsNumber != rightIsNumber) {
            return leftIsNumber ? -1 : 1;
        }
        return left.compareToIgnoreCase(right);
    }

    private static boolean isUnsignedNumber(String value) {
        return !value.isEmpty()
                && value.chars().allMatch(character -> character >= '0' && character <= '9');
    }
}
