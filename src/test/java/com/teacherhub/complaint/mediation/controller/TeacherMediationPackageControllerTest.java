package com.teacherhub.complaint.mediation.controller;

import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent;
import com.teacherhub.complaint.mediation.dto.MediationPackageDownload;
import com.teacherhub.complaint.mediation.dto.MediationPackageResponse;
import com.teacherhub.complaint.mediation.service.MediationPackageService;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherMediationPackageControllerTest {

    @Test
    void createsFindsAndDownloadsPackageThroughSeparatedRoutes() throws Exception {
        var service = mock(MediationPackageService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        var generatedAt = LocalDateTime.of(2026, 11, 20, 10, 30);
        var packageId = "005b45e1-e144-4b1f-8a40-bba6fb8356df";
        var contentSnapshot = new MediationPackageContent(
                1,
                packageId,
                generatedAt,
                new MediationPackageContent.ComplaintSummary(42L, ComplaintStatus.OPEN),
                false,
                List.of(),
                List.of(),
                new MediationPackageContent.HistorySnapshot(true, List.of(), List.of())
        );
        var response = new MediationPackageResponse(
                packageId,
                42L,
                1,
                false,
                "a".repeat(64),
                generatedAt,
                contentSnapshot
        );
        byte[] downloadBody = "{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8);

        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(user));
        when(service.create(7L, 42L, false, "package-42-1")).thenReturn(response);
        when(service.find(7L, 42L, packageId)).thenReturn(response);
        when(service.download(7L, 42L, packageId)).thenReturn(new MediationPackageDownload(
                "mediation-package-42-" + packageId + ".json",
                "a".repeat(64),
                downloadBody
        ));

        var mvc = MockMvcBuilders.standaloneSetup(
                new TeacherMediationPackageController(service, users)
        ).build();
        var auth = new UsernamePasswordAuthenticationToken(
                "teacher@test.com", null, List.of()
        );

        mvc.perform(post("/teacher/complaints/42/mediation-packages")
                        .principal(auth)
                        .header("Idempotency-Key", "package-42-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includeOriginalMessages\":false}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.packageId").value(packageId))
                .andExpect(jsonPath("$.content.originalMessagesIncluded").value(false));

        mvc.perform(get("/teacher/complaints/42/mediation-packages/{packageId}", packageId)
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checksumSha256").value("a".repeat(64)));

        mvc.perform(get(
                        "/teacher/complaints/42/mediation-packages/{packageId}/download",
                        packageId
                ).principal(auth))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-SHA256", "a".repeat(64)))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().bytes(downloadBody));

        verify(service).create(7L, 42L, false, "package-42-1");
        verify(service).find(7L, 42L, packageId);
        verify(service).download(7L, 42L, packageId);
    }
}
