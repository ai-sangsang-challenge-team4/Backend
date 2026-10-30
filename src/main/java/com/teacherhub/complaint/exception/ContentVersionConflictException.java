package com.teacherhub.complaint.exception;

public class ContentVersionConflictException extends RuntimeException {

    public ContentVersionConflictException(Long expectedVersion, long currentVersion) {
        super("추가 문의 초안이 다른 기기에서 변경되었습니다. 기대 버전: "
                + expectedVersion + ", 현재 버전: " + currentVersion);
    }

    public static ContentVersionConflictException expectedVersionRequired(long currentVersion) {
        return new ContentVersionConflictException(null, currentVersion);
    }
}
