package com.teacherhub.risk.dto.detection;

public record EvidenceSpan(
        String text,
        Integer start,
        Integer end
) {
    public EvidenceSpan {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("근거 문구가 필요합니다.");
        }
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException("근거 시작·종료 위치는 함께 제공해야 합니다.");
        }
        if (start != null && (start < 0 || end <= start)) {
            throw new IllegalArgumentException("근거 위치 범위가 올바르지 않습니다.");
        }
    }

    public static EvidenceSpan unlocated(String text) {
        return new EvidenceSpan(text, null, null);
    }

    public EvidenceSpan locateIn(String content) {
        if (start != null || content == null) {
            return this;
        }
        int index = content.indexOf(text);
        return index < 0 ? this : new EvidenceSpan(text, index, index + text.length());
    }
}
