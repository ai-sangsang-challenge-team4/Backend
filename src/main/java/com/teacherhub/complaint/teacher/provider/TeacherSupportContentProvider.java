package com.teacherhub.complaint.teacher.provider;

public interface TeacherSupportContentProvider {
    // 완충 요약 제공
    String createBufferedSummary(String originalContent);
}
