package com.teacherhub.ai;

import com.teacherhub.complaint.teacher.provider.PythonTeacherSupportContentProvider;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator;
import com.teacherhub.risk.analyzer.LlmRiskAnalyzer;
import com.teacherhub.risk.analyzer.PythonComplaintRevisionGenerator;
import com.teacherhub.risk.analyzer.PythonLlmRiskAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiProviderWiringTest {

    @Autowired
    private LlmRiskAnalyzer llmRiskAnalyzer;

    @Autowired
    private ComplaintRevisionGenerator complaintRevisionGenerator;

    @Autowired
    private TeacherSupportContentProvider teacherSupportContentProvider;

    @Test
    void usesRealPythonAiProvidersInApplicationContext() {
        assertThat(llmRiskAnalyzer).isInstanceOf(PythonLlmRiskAnalyzer.class);
        assertThat(complaintRevisionGenerator)
                .isInstanceOf(PythonComplaintRevisionGenerator.class);
        assertThat(teacherSupportContentProvider)
                .isInstanceOf(PythonTeacherSupportContentProvider.class);
    }
}
