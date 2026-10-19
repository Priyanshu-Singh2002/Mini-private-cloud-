package com.minicloud.controller;

import com.minicloud.dto.UserStorageStats;
import com.minicloud.service.AdminUserService;
import com.minicloud.service.StorageQuotaService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminQuotaAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private StorageQuotaService storageQuotaService;

    @Test
    void quotaUpdateUsesAdminUsersPath() throws Exception {
        when(storageQuotaService.updateQuota(eq(3L), any()))
                .thenReturn(new UserStorageStats(
                        3L,
                        "alice",
                        "alice@example.com",
                        "USER",
                        "ACTIVE",
                        0L,
                        0L,
                        209715200L,
                        209715200L,
                        0.0));

        mockMvc.perform(patch("/api/admin/users/3/quota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageQuotaBytes\": 209715200}"))
                .andExpect(status().isOk());

        verify(storageQuotaService).updateQuota(eq(3L), any());
    }
}
