package com.teacherhub.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ComplaintRequest {

    // 자녀가 여러명인 경우
    private Long studentId;

    @NotBlank(message = "민원 내용을 입력해주세요.")
    private String content;

}
