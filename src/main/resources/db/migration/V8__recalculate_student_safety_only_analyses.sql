-- studentSafetyConcern은 원시 안전 신호로만 보존하고 Emergency 강제 승격에는
-- 사용하지 않는다. 구 정책으로 저장된 분석도 현재 태그와 최고 Severity 정책으로
-- 재계산해 동일 내용 재사용 시 과거의 100점 결과가 노출되지 않게 한다.
UPDATE risk_analyses AS analysis
SET risk_score = LEAST(
        100,
        COALESCE((
            SELECT SUM(tag.default_score)
            FROM complaint_risk_tags AS result
            JOIN risk_tags AS tag ON tag.id = result.risk_tag_id
            WHERE result.risk_analysis_id = analysis.id
              AND (
                  result.final_state = 'PRESENT'
                  OR (result.final_state IS NULL AND result.final_detected = TRUE)
              )
        ), 0)
        + CASE COALESCE((
            SELECT MAX(result.severity)
            FROM complaint_risk_tags AS result
            WHERE result.risk_analysis_id = analysis.id
              AND (
                  result.llm_state = 'PRESENT'
                  OR (result.llm_state IS NULL AND result.llm_detected = TRUE)
              )
        ), 0)
            WHEN 2 THEN 19
            WHEN 3 THEN 50
            ELSE 0
        END
    )
WHERE analysis.student_safety_concern = TRUE
  AND (analysis.credible_threat IS NULL OR analysis.credible_threat = FALSE)
  AND (analysis.privacy_exposure IS NULL OR analysis.privacy_exposure = FALSE);

UPDATE risk_analyses
SET risk_level = CASE
        WHEN risk_score <= 20 THEN 'LOW'
        WHEN risk_score <= 51 THEN 'MEDIUM'
        WHEN risk_score <= 79 THEN 'HIGH'
        ELSE 'EMERGENCY'
    END
WHERE student_safety_concern = TRUE
  AND (credible_threat IS NULL OR credible_threat = FALSE)
  AND (privacy_exposure IS NULL OR privacy_exposure = FALSE);
