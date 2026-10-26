package com.teacherhub.user.repository;

import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findFirstByRoleOrderByIdAsc(UserRole role);
}
