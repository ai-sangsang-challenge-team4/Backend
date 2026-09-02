package com.teacherhub.school.entity;

import com.teacherhub.user.entity.Teacher;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@Getter
@Entity
@Table(name = "school_classes")
public class SchoolClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(nullable = false)
    private int grade;

    @Column(name = "class_no", nullable = false)
    private int classNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homeroom_teacher_id", nullable = false)
    private Teacher homeroomTeacher;

    public SchoolClass(
            int academicYear,
            int grade,
            int classNo,
            Teacher homeroomTeacher
    ) {
        this.academicYear = academicYear;
        this.grade = grade;
        this.classNo = classNo;
        this.homeroomTeacher = homeroomTeacher;
    }
}
