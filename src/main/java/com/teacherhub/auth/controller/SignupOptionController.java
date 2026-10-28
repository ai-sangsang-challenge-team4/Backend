package com.teacherhub.auth.controller;

import com.teacherhub.auth.dto.SignupOptionResponse;
import com.teacherhub.auth.service.SignupOptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;
import java.util.List;

@Tag(name = "Signup Options", description = "운영 DB 기반 회원가입 선택 항목 조회 API")
@Validated
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class SignupOptionController {

    private final SignupOptionService signupOptionService;

    @Operation(summary = "학교 검색", description = "서비스 운영 DB에 등록된 학교만 최대 20개 조회합니다.")
    @GetMapping("/schools")
    public List<SignupOptionResponse> schools(
            @Parameter(description = "학교명 검색어. 생략하면 학교명순으로 조회합니다.")
            @RequestParam(required = false) String keyword
    ) {
        return signupOptionService.schools(keyword);
    }

    @Operation(summary = "학교 학급 조회", description = "선택한 학교의 학년도·학년별 학급을 조회합니다.")
    @GetMapping("/schools/{schoolId}/classes")
    public List<SignupOptionResponse> schoolClasses(
            @PathVariable Long schoolId,
            @RequestParam(required = false)
            @Min(2000) @Max(2100) Integer academicYear,
            @RequestParam(required = false) @Min(1) @Max(6) Integer grade
    ) {
        int selectedYear = academicYear == null ? Year.now().getValue() : academicYear;
        return signupOptionService.schoolClasses(schoolId, selectedYear, grade);
    }

    @Operation(
            summary = "학급 학생 번호 조회",
            description = "선택한 학급의 학생 ID와 번호만 조회합니다. 학생 이름은 가입 요청에서 별도로 검증합니다."
    )
    @GetMapping("/classes/{schoolClassId}/students")
    public List<SignupOptionResponse> students(@PathVariable Long schoolClassId) {
        return signupOptionService.students(schoolClassId);
    }
}
