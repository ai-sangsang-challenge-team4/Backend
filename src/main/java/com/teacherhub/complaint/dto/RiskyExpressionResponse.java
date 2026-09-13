package com.teacherhub.complaint.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RiskyExpressionResponse {
   //  private String type; 각 표현마다 type을 부여하는 건지?

    private String expression;
    private String reason;
}
