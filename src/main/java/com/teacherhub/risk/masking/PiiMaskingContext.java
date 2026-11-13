package com.teacherhub.risk.masking;

import com.teacherhub.complaint.entity.Complaint;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record PiiMaskingContext(
        List<String> studentNames,
        List<String> teacherNames,
        List<String> studentIdentifiers,
        List<String> teacherIdentifiers
) {
    public PiiMaskingContext {
        studentNames = normalize(studentNames, 2);
        teacherNames = normalize(teacherNames, 2);
        studentIdentifiers = normalize(studentIdentifiers, 3);
        teacherIdentifiers = normalize(teacherIdentifiers, 3);
    }

    public static PiiMaskingContext empty() {
        return new PiiMaskingContext(List.of(), List.of(), List.of(), List.of());
    }

    public static PiiMaskingContext from(Complaint complaint) {
        if (complaint == null) {
            return empty();
        }
        var student = complaint.getStudent();
        var teacher = complaint.getTeacher();
        return new PiiMaskingContext(
                student == null ? List.of() : nullableValue(student.getName()),
                teacher == null
                        ? List.of()
                        : Stream.of(
                                teacher.getName(),
                                teacher.getUser() == null ? null : teacher.getUser().getName()
                        ).filter(Objects::nonNull).toList(),
                student == null ? List.of() : nullableValue(student.getStudentNo()),
                teacher == null ? List.of() : nullableValue(teacher.getEmployeeNo())
        );
    }

    private static List<String> nullableValue(String value) {
        return value == null ? List.of() : List.of(value);
    }

    private static List<String> normalize(List<String> values, int minimumLength) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> value.length() >= minimumLength)
                .distinct()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();
    }
}
