package com.teacherhub.auth.service;

import com.teacherhub.auth.dto.LoginRequest;
import com.teacherhub.auth.dto.LoginResponse;
import com.teacherhub.auth.dto.SignupRequest;
import com.teacherhub.auth.dto.SignupResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.parent.entity.Parent;
import com.teacherhub.parent.repository.ParentRepository;
import com.teacherhub.security.JwtTokenProvider;
import com.teacherhub.teacher.entity.Teacher;
import com.teacherhub.teacher.repository.TeacherRepository;
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
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        if (request.role() == UserRole.TEACHER) {
            if (request.employeeNo() == null || request.employeeNo().isBlank()) {
                throw new IllegalArgumentException("교사는 교직원 번호를 입력해야 합니다.");
            }

            if (teacherRepository.existsByEmployeeNo(request.employeeNo())) {
                throw new IllegalArgumentException("이미 사용 중인 교직원 번호입니다.");
            }
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                request.email(),
                encodedPassword,
                request.name(),
                request.role()
        );

        User savedUser = userRepository.save(user);

        if (savedUser.getRole() == UserRole.PARENT) {
            parentRepository.save(new Parent(savedUser));
        }

        if (savedUser.getRole() == UserRole.TEACHER) {
            teacherRepository.save(
                    new Teacher(savedUser, request.employeeNo())
            );
        }

        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getRole(),
                savedUser.getCreatedAt()
        );
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