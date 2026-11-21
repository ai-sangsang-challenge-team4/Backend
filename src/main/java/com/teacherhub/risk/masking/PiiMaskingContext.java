package com.teacherhub.risk.masking;

import com.teacherhub.complaint.entity.Complaint;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record PiiMaskingContext(
        List<String> studentNames,
        List<String> teacherNames,
        List<String> studentIdentifiers,
        List<String> teacherIdentifiers,
        List<String> additionalNames
) {
    public PiiMaskingContext {
        studentNames = normalize(studentNames, 2);
        teacherNames = normalize(teacherNames, 2);
        studentIdentifiers = normalize(studentIdentifiers, 1);
        teacherIdentifiers = normalize(teacherIdentifiers, 1);
        additionalNames = normalize(additionalNames, 2);
    }

    public PiiMaskingContext(
            List<String> studentNames,
            List<String> teacherNames,
            List<String> studentIdentifiers,
            List<String> teacherIdentifiers
    ) {
        this(
                studentNames,
                teacherNames,
                studentIdentifiers,
                teacherIdentifiers,
                List.of()
        );
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

    public PiiMaskingContext withAdditionalNames(List<String> names) {
        List<String> knownNames = Stream.concat(
                studentNames.stream(),
                teacherNames.stream()
        ).toList();
        List<String> newNames = normalize(names, 2).stream()
                .filter(name -> !knownNames.contains(name))
                .toList();
        return new PiiMaskingContext(
                studentNames,
                teacherNames,
                studentIdentifiers,
                teacherIdentifiers,
                Stream.concat(additionalNames.stream(), newNames.stream()).toList()
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
