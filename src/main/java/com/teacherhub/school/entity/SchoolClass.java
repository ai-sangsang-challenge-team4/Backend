package com.teacherhub.school.entity;

import com.teacherhub.user.entity.Teacher;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;


@NoArgsConstructor
@Getter
@Entity
@Table(name = "school_classes")
public class SchoolClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(nullable = false)
    private int grade;

    @Column(name = "class_no", nullable = false)
    private int classNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homeroom_teacher_id", nullable = false)
    // 담임 교사
    private Teacher homeroomTeacher;

    @ManyToMany
    @JoinTable(
            name = "school_class_teachers",
            joinColumns = @JoinColumn(name = "school_class_id"),
            inverseJoinColumns = @JoinColumn(name = "teacher_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_school_class_teacher",
                    columnNames = {"school_class_id", "teacher_id"}
            )
    )
    // 학급에 지정된 교사
    private Set<Teacher> assignedTeachers = new LinkedHashSet<>();

    public SchoolClass(
            int academicYear,
            int grade,
            int classNo,
            Teacher homeroomTeacher
    ) {
        this(null, academicYear, grade, classNo, homeroomTeacher);
    }

    public SchoolClass(
            School school,
            int academicYear,
            int grade,
            int classNo,
            Teacher homeroomTeacher
    ) {
        this.school = school;
        this.academicYear = academicYear;
        this.grade = grade;
        this.classNo = classNo;
        this.homeroomTeacher = homeroomTeacher;
        this.assignedTeachers.add(homeroomTeacher);
    }

    public void assignTeacher(Teacher teacher) {
        assignedTeachers.add(teacher);
    }

    public boolean hasAssignedTeacher(Teacher teacher) {
        return teacher.getUser() != null && (homeroomTeacher.getId().equals(teacher.getId())
                || assignedTeachers.stream().anyMatch(candidate -> candidate.getId().equals(teacher.getId())));
    }

    public Set<Teacher> availableTeachers() {
        LinkedHashSet<Teacher> teachers = new LinkedHashSet<>();
        if (homeroomTeacher.getUser() != null) {
            teachers.add(homeroomTeacher);
        }
        assignedTeachers.stream()
                .filter(teacher -> teacher.getUser() != null)
                .forEach(teachers::add);
        return teachers;
    }
}
