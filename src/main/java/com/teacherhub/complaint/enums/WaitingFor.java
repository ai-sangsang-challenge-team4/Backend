package com.teacherhub.complaint.enums;

public enum WaitingFor {
    // 부모가 답변을 확인하고 답변 또는 민원을 종료해야 하는 상태
    PARENT,
    // 교사가 민원에 대한 답변을 작성하고 제출해야 하는 상태
    TEACHER,
    // 민원이 종료되어 더 이상 기다릴 필요가 없는 상태
    NONE
}
