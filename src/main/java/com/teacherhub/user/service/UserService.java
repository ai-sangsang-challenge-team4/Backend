package com.teacherhub.user.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.user.dto.MyStudentResponse;
import com.teacherhub.user.dto.UserInfoResponse;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.ParentStudentRepository;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ParentStudentRepository parentStudentRepository;

    @Transactional(readOnly = true)
    public UserInfoResponse getMyInfo(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UnauthorizedException("사용자를 찾을 수 없습니다.")
                );

        List<MyStudentResponse> students = user.getRole() == UserRole.PARENT
                ? parentStudentRepository.findByParent_User_IdOrderByStudent_NameAscStudent_IdAsc(userId)
                        .stream()
                        .map(link -> MyStudentResponse.from(link.getStudent()))
                        .toList()
                : List.of();

        return new UserInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                students
        );
    }
}
