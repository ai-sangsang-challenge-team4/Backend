-- 저장된 점수는 유지하고 현재 위험도 경계 정책에 맞춰 등급만 재분류한다.
UPDATE risk_analyses
SET risk_level = CASE
        WHEN risk_score <= 20 THEN 'LOW'
        WHEN risk_score <= 50 THEN 'MEDIUM'
        WHEN risk_score <= 80 THEN 'HIGH'
        ELSE 'EMERGENCY'
    END;
