package com.teacherhub.auth.service;

import com.teacherhub.auth.dto.LoginRequest;
import com.teacherhub.auth.dto.LoginResponse;
import com.teacherhub.auth.dto.SignupRequest;
import com.teacherhub.auth.dto.SignupResponse;
import com.teacherhub.auth.exception.SignupConflictException;
import com.teacherhub.auth.exception.SignupReferenceNotFoundException;
import com.teacherhub.auth.exception.SignupValidationException;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.ParentStudent;
import com.teacherhub.user.repository.ParentRepository;
import com.teacherhub.user.repository.ParentStudentRepository;
import com.teacherhub.security.JwtTokenProvider;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.repository.TeacherRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentClassRepository studentClassRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new SignupConflictException("이미 사용 중인 이메일입니다.");
        }

        if (request.role() != UserRole.PARENT && request.role() != UserRole.TEACHER) {
            throw new SignupValidationException("학부모 또는 교사 역할로만 회원가입할 수 있습니다.");
        }
        if (request.schoolId() == null) {
            throw new SignupValidationException("학교를 선택해야 합니다.");
        }

        StudentClass studentClass = request.role() == UserRole.PARENT
                ? validateParentSignup(request)
                : null;
        Teacher teacher = request.role() == UserRole.TEACHER
                ? validateTeacherSignup(request)
                : null;

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                request.email(),
                encodedPassword,
                request.name(),
                request.role()
        );

        User savedUser = userRepository.save(user);

        if (savedUser.getRole() == UserRole.PARENT) {
            Parent parent = parentRepository.save(new Parent(savedUser));
            parentStudentRepository.save(new ParentStudent(
                    parent,
                    studentClass.getStudent()
            ));
        }

        if (savedUser.getRole() == UserRole.TEACHER) {
            teacher.register(savedUser);
        }

        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getRole(),
                savedUser.getCreatedAt()
        );
    }

    private StudentClass validateParentSignup(SignupRequest request) {
        if (request.schoolClassId() == null || request.studentId() == null) {
            throw new SignupValidationException("학부모는 자녀의 학급과 학생 번호를 선택해야 합니다.");
        }
        if (request.studentName() == null || request.studentName().isBlank()) {
            throw new SignupValidationException("학부모는 자녀 이름을 입력해야 합니다.");
        }

        StudentClass studentClass = studentClassRepository
                .findBySchoolClass_School_IdAndSchoolClass_IdAndStudent_Id(
                        request.schoolId(),
                        request.schoolClassId(),
                        request.studentId()
                )
                .orElseThrow(() -> new SignupReferenceNotFoundException(
                        "선택한 학교와 학급에서 학생 정보를 찾을 수 없습니다."
                ));

        if (!studentClass.getStudent().getName().equals(request.studentName().trim())) {
            throw new SignupValidationException("학생 번호와 자녀 이름이 일치하지 않습니다.");
        }
        return studentClass;
    }

    private Teacher validateTeacherSignup(SignupRequest request) {
        if (request.employeeNo() == null || request.employeeNo().isBlank()) {
            throw new SignupValidationException("교사는 교직원 번호를 입력해야 합니다.");
        }

        Teacher teacher = teacherRepository
                .findForSignupByEmployeeNo(request.employeeNo().trim())
                .orElseThrow(() -> new SignupReferenceNotFoundException(
                        "교직원 번호와 일치하는 교사 정보를 찾을 수 없습니다."
                ));

        if (teacher.getSchool() == null
                || !teacher.getSchool().getId().equals(request.schoolId())) {
            throw new SignupReferenceNotFoundException(
                    "학교와 교직원 번호가 일치하는 교사 정보를 찾을 수 없습니다."
            );
        }
        if (!teacher.getName().equals(request.name().trim())) {
            throw new SignupValidationException("교직원 명부의 이름과 입력한 이름이 일치하지 않습니다.");
        }
        if (teacher.getUser() != null) {
            throw new SignupConflictException("이미 가입된 교직원입니다.");
        }
        return teacher;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.email())
        .orElseThrow(() ->
                new UnauthorizedException(
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                )
        );

if (!passwordEncoder.matches(request.password(), user.getPassword())) {
    throw new UnauthorizedException(
            "이메일 또는 비밀번호가 올바르지 않습니다."
    );
}

        String accessToken = jwtTokenProvider.createAccessToken(user);

        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtTokenProvider.getExpirationSeconds(),
                new LoginResponse.UserInfo(
                        user.getId(),
                        user.getEmail(),
                        user.getName(),
                        user.getRole()
                )
        );
    }
}
