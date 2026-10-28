package com.teacherhub.user.entity;

import com.teacherhub.school.entity.School;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "teachers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "employee_no", nullable = false, unique = true, length = 100)
    private String employeeNo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Teacher(User user, String employeeNo) {
        this(user, employeeNo, null);
    }

    public Teacher(User user, String employeeNo, School school) {
        this.user = user;
        this.school = school;
        this.name = user.getName();
        this.employeeNo = employeeNo;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Teacher(School school, String name, String employeeNo) {
        this.school = school;
        this.name = name;
        this.employeeNo = employeeNo;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void register(User user) {
        if (this.user != null) {
            throw new IllegalStateException("이미 가입된 교직원입니다.");
        }
        this.user = user;
        this.updatedAt = LocalDateTime.now();
    }
}
