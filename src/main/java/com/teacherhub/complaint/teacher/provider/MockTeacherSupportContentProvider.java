package com.teacherhub.complaint.teacher.provider;

import org.springframework.stereotype.Component;

@Component
public class MockTeacherSupportContentProvider implements TeacherSupportContentProvider {

    // 민원 완충 요약 (상세조회용)
    @Override
    public String createBufferedSummary(String originalContent) {
        return "[MOCK] 학부모가 전달한 민원의 완충 요약을 확인해 주세요.";
    }

    @Override
    public GeneratedResponseDraft createResponseDraft(String originalContent) {
        return new GeneratedResponseDraft(
                "안녕하세요, 학부모님.\n\n"
                        + "먼저 상담 일정 및 조치 내용과 관련하여 불편과 걱정을 드린 점 진심으로 송구합니다.\n\n"
                        + "말씀해 주신 상황은 학교에서도 매우 중요하게 받아들이고 있으며, "
                        + "현재 관련 내용을 파악하고 사실 관계를 확인 중에 있습니다. "
                        + "정확한 확인이 완료되는 대로 추가 안내드리겠습니다.\n\n"
                        + "우리 학교는 학생과 학부모님의 상담을 중요하게 생각하며, "
                        + "보호자님과 직접 말씀드리고 상황을 설명드리는 시간을 갖고 싶습니다.\n\n"
                        + "감사합니다.",
                "[MOCK] 민원 내용을 바탕으로 생성한 교사용 답변 초안입니다.",
                "mock-teacher-response-v1"
        );
    }
}
