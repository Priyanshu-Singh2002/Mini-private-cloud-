package com.minicloud.controller;

import com.minicloud.config.JwtConfig;
import com.minicloud.config.SecurityConfig;
import com.minicloud.dto.AuditLogPageResponse;
import com.minicloud.dto.AuditLogResponse;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;
import com.minicloud.service.AdminAuditLogService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuditLogController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtConfig.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@TestPropertySource(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AdminAuditLogAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuditLogService adminAuditLogService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    @BeforeEach
    void setUp() {
        AppUser activeUser = new AppUser();
        activeUser.setUsername("admin");
        activeUser.setEmail("admin@example.com");
        activeUser.setPasswordHash("hash");
        activeUser.setStatus("ACTIVE");

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(activeUser));
    }

    @Test
    void adminCanListAuditHistory() throws Exception {
        when(adminAuditLogService.search(
                eq(0), eq(20), eq("USER_APPROVED"), eq("USER"), eq("alice"),
                eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 24))))
                .thenReturn(new AuditLogPageResponse(
                        List.of(new AuditLogResponse(
                                7L,
                                1L,
                                "MC-ACTOR001",
                                "alice",
                                "USER_APPROVED",
                                "USER",
                                5L,
                                "MC-ENTITY1",
                                "Approved alice",
                                "SUCCESS",
                                LocalDateTime.of(2026, 11, 24, 10, 0))),
                        0,
                        20,
                        1,
                        1,
                        4,
                        null,
                        List.of("USER_APPROVED"),
                        List.of("USER")));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("page", "0")
                        .param("size", "20")
                        .param("action", "USER_APPROVED")
                        .param("entityType", "USER")
                        .param("search", "alice")
                        .param("fromDate", "2026-11-01")
                        .param("toDate", "2026-11-24")
                        .with(jwt().jwt(jwt -> jwt.claim("userId", 1L).claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].actorUserId").value(1))
                .andExpect(jsonPath("$.content[0].actorUniqueId").value("MC-ACTOR001"))
                .andExpect(jsonPath("$.content[0].actorUsername").value("alice"))
                .andExpect(jsonPath("$.content[0].action").value("USER_APPROVED"))
                .andExpect(jsonPath("$.content[0].entityType").value("USER"))
                .andExpect(jsonPath("$.content[0].entityId").value(5))
                .andExpect(jsonPath("$.content[0].entityUniqueId").value("MC-ENTITY1"))
                .andExpect(jsonPath("$.content[0].description").value("Approved alice"))
                .andExpect(jsonPath("$.totalRecords").value(4))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void nonAdminCannotListAuditHistory() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .with(jwt().jwt(jwt -> jwt.claim("userId", 2L).claim("role", "USER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        verify(adminAuditLogService, never()).search(
                anyInt(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void anonymousCannotListAuditHistory() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized());

        verify(adminAuditLogService, never()).search(
                anyInt(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void adminCanExportFilteredCsv() throws Exception {
        doAnswer(invocation -> {
            java.io.Writer writer = invocation.getArgument(5);
            writer.write("id,actorUserId\r\n1,2\r\n");
            return null;
        }).when(adminAuditLogService).writeCsv(
                eq("USER_APPROVED"), isNull(), eq("alice"),
                eq(LocalDate.of(2026, 11, 1)), isNull(), any());

        mockMvc.perform(get("/api/admin/audit-logs/export")
                        .param("action", "USER_APPROVED")
                        .param("search", "alice")
                        .param("fromDate", "2026-11-01")
                        .with(jwt().jwt(jwt -> jwt.claim("userId", 1L).claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("audit-logs.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id,actorUserId")));
    }

    @Test
    void nonAdminCannotExportAuditHistory() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs/export")
                        .with(jwt().jwt(jwt -> jwt.claim("userId", 2L).claim("role", "USER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        verify(adminAuditLogService, never()).writeCsv(
                any(), any(), any(), any(), any(), any());
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
