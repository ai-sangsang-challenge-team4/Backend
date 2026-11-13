package com.teacherhub.common.validation;

import com.teacherhub.complaint.parent.dto.complaint.ComplaintCreateRequest;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintDraftUpdateRequest;
import com.teacherhub.complaint.parent.dto.message.ParentMessageRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLengthValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsFiveThousandCharactersAtBoundary() {
        String content = "가".repeat(5_000);

        assertThat(validator.validate(new ComplaintCreateRequest(1L, 2L, "제목", content))).isEmpty();
        assertThat(validator.validate(new ComplaintDraftUpdateRequest(content))).isEmpty();
        assertThat(validator.validate(new ParentMessageRequest(content, null))).isEmpty();
    }

    @Test
    void rejectsContentLongerThanFiveThousandCharacters() {
        String content = "가".repeat(5_001);

        assertThat(validator.validate(new ComplaintCreateRequest(1L, 2L, "제목", content))).hasSize(1);
        assertThat(validator.validate(new ComplaintDraftUpdateRequest(content))).hasSize(1);
        assertThat(validator.validate(new ParentMessageRequest(content, null))).hasSize(1);
    }
}
