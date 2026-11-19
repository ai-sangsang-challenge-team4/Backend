package com.teacherhub.risk.dto.response.common;

import com.teacherhub.risk.dto.llm.SafetyFlags;
import com.teacherhub.risk.enums.SafetySignalCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

// 외부 AI의 원시 Safety Flag를 역할별 화면에서 공통으로 사용하는 안전 신호 계약으로 변환합니다.
public record EmergencyOverrideResponse(
        @Schema(description = "Safety Flag에 의해 Emergency가 강제 적용됐는지 여부")
        boolean applied,
        @Schema(description = "Emergency 강제 적용에 기여한 안전 신호 목록")
        List<SafetySignalResponse> safetySignals
) {
    public static EmergencyOverrideResponse from(SafetyFlags flags) {
        if (flags == null) {
            return notApplied();
        }

        List<SafetySignalResponse> signals = new ArrayList<>();
        if (Boolean.TRUE.equals(flags.credibleThreat())) {
            signals.add(new SafetySignalResponse(
                    SafetySignalCode.CREDIBLE_THREAT,
                    "구체적 위해 위협",
                    "구체적인 위해 위협 신호가 확인되어 일반 위험 태그 점수와 별도로 긴급 단계로 상향되었습니다."
            ));
        }
        if (Boolean.TRUE.equals(flags.studentSafetyConcern())) {
            signals.add(new SafetySignalResponse(
                    SafetySignalCode.STUDENT_SAFETY_CONCERN,
                    "학생 안전 우려",
                    "학생 안전 우려 신호가 확인되어 일반 위험 태그 점수와 별도로 긴급 단계로 상향되었습니다."
            ));
        }
        if (Boolean.TRUE.equals(flags.privacyExposure())) {
            signals.add(new SafetySignalResponse(
                    SafetySignalCode.PRIVACY_EXPOSURE,
                    "중대한 개인정보 노출",
                    "중대한 개인정보 노출 또는 유포 위험 신호가 확인되어 일반 위험 태그 점수와 별도로 긴급 단계로 상향되었습니다."
            ));
        }
        return new EmergencyOverrideResponse(!signals.isEmpty(), List.copyOf(signals));
    }

    public static EmergencyOverrideResponse notApplied() {
        return new EmergencyOverrideResponse(false, List.of());
    }

    public record SafetySignalResponse(
            @Schema(description = "안정된 안전 신호 코드")
            SafetySignalCode code,
            @Schema(description = "화면 표시용 안전 신호 제목")
            String title,
            @Schema(description = "Emergency 승격 사유 안내")
            String description
    ) {
    }
}
