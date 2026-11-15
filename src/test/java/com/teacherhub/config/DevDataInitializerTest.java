package com.teacherhub.config;

import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskTagRepository;
import com.teacherhub.school.entity.School;
import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.SchoolRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.repository.ParentRepository;
import com.teacherhub.user.repository.ParentStudentRepository;
import com.teacherhub.user.repository.StudentRepository;
import com.teacherhub.user.repository.TeacherRepository;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevDataInitializerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ParentRepository parentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ParentStudentRepository parentStudentRepository;
    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private SchoolClassRepository schoolClassRepository;
    @Mock
    private StudentClassRepository studentClassRepository;
    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private ComplaintEventRepository complaintEventRepository;
    @Mock
    private RiskAnalysisRepository riskAnalysisRepository;
    @Mock
    private RiskTagRepository riskTagRepository;
    @Mock
    private ComplaintRiskTagRepository complaintRiskTagRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private Environment environment;
    @Mock
    private ApplicationArguments arguments;

    @InjectMocks
    private DevDataInitializer initializer;

    @Test
    void supportsDevAndExplicitDemoSeedProfiles() {
        Profile profile = DevDataInitializer.class.getAnnotation(Profile.class);

        assertThat(profile.value()).containsExactlyInAnyOrder("dev", "demo-seed");
    }

    @Test
    void skipsCreationWhenAllDemoAccountsAlreadyExist() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(userRepository.existsByEmail("parent@test.com")).thenReturn(true);
        when(userRepository.existsByEmail("teacher@test.com")).thenReturn(true);
        when(userRepository.existsByEmail("admin@test.com")).thenReturn(true);

        initializer.run(arguments);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void rejectsPartialDemoAccountsBeforeWritingData() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(userRepository.existsByEmail("parent@test.com")).thenReturn(true);
        when(userRepository.existsByEmail("teacher@test.com")).thenReturn(false);
        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);

        assertThatThrownBy(() -> initializer.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("일부 데모 계정만 존재");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void requiresExplicitPasswordWhenDemoSeedProfileIsActive(String password) {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "demo-seed"});
        when(environment.getProperty("DEMO_SEED_PASSWORD")).thenReturn(password);

        assertThatThrownBy(() -> initializer.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_SEED_PASSWORD");

        verify(userRepository, never()).save(any());
        verify(schoolRepository, never()).save(any());
    }

    @Test
    void reusesExistingDemoSchool() {
        School existingSchool = new School("SCHOOL-001", "기존 학교");
        when(schoolRepository.findByCode("SCHOOL-001")).thenReturn(Optional.of(existingSchool));

        School school = initializer.findOrCreateDemoSchool();

        assertThat(school).isSameAs(existingSchool);
        verify(schoolRepository, never()).save(any());
    }
}
