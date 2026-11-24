
package com.minicloud.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minicloud.dto.UserStorageStats;
import com.minicloud.service.AdminUserService;
import com.minicloud.service.StorageQuotaService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AdminQuotaAuthorizationTest {

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private StorageQuotaService storageQuotaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminUserController controller = new AdminUserController(
                adminUserService,
                storageQuotaService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new FixedJwtArgumentResolver(createAdminJwt()))
                .build();
    }

    @Test
    void quotaUpdateUsesAdminUsersPath() throws Exception {

        when(storageQuotaService.updateQuota(
                eq(3L),
                any(),
                eq(1L)))
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

        mockMvc.perform(
                patch("/api/admin/users/3/quota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageQuotaBytes\":209715200}"))
                .andExpect(status().isOk());

        verify(storageQuotaService).updateQuota(
                eq(3L),
                any(),
                eq(1L));
    }

    private Jwt createAdminJwt() {
        return new Jwt(
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("userId", 1L, "role", "ADMIN"));
    }

    private static final class FixedJwtArgumentResolver
            implements HandlerMethodArgumentResolver {

        private final Jwt jwt;

        private FixedJwtArgumentResolver(Jwt jwt) {
            this.jwt = jwt;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && parameter.getParameterType().equals(Jwt.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return jwt;
        }
    }
}
