package com.teacherhub.auth.dto;

import com.teacherhub.school.entity.School;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.user.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 화면에서 사용하는 학교·학급·학생 번호 공통 선택 항목")
public record SignupOptionResponse(

        @Schema(description = "선택 항목 종류", example = "SCHOOL")
        OptionType type,

        @Schema(description = "선택 항목 ID", example = "1")
        Long id,
        @Schema(description = "화면 표시명", example = "상상초등학교")
        String displayName,

        @Schema(description = "학교 코드 또는 학생 번호", example = "SCHOOL-001", nullable = true)
        String referenceCode,

        @Schema(description = "학년도", example = "2026", nullable = true)
        Integer academicYear,

        @Schema(description = "학년", example = "3", nullable = true)
        Integer grade,

        @Schema(description = "반", example = "2", nullable = true)
        Integer classNo
) {
    public enum OptionType {
        SCHOOL,
        SCHOOL_CLASS,
        STUDENT
    }

    public static SignupOptionResponse from(School school) {
        return new SignupOptionResponse(
                OptionType.SCHOOL,
                school.getId(),
                school.getName(),
                school.getCode(),
                null,
                null,
                null
        );
    }

    public static SignupOptionResponse from(SchoolClass schoolClass) {
        return new SignupOptionResponse(
                OptionType.SCHOOL_CLASS,
                schoolClass.getId(),
                schoolClass.getGrade() + "학년 " + schoolClass.getClassNo() + "반",
                null,
                schoolClass.getAcademicYear(),
                schoolClass.getGrade(),
                schoolClass.getClassNo()
        );
    }

    public static SignupOptionResponse from(Student student) {
        return new SignupOptionResponse(
                OptionType.STUDENT,
                student.getId(),
                student.getStudentNo() + "번",
                student.getStudentNo(),
                null,
                null,
                null
        );
    }
}
