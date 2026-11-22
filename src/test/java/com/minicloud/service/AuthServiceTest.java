
package com.minicloud.service;

import com.minicloud.dto.LoginRequest;
import com.minicloud.dto.LoginResponse;
import com.minicloud.dto.RegisterRequest;
import com.minicloud.dto.RegisterResponse;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;
import com.minicloud.security.JwtService;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();

        ReflectionTestUtils.setField(user, "id", 1L);

        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setUniqueUserId("MC-ABCD2345");
        user.setPasswordHash("encoded-password");
        user.setStatus("ACTIVE");
    }

    @Test
    void registerShouldSaveUserWithEncodedPassword() {

        RegisterRequest request = new RegisterRequest(
                "testuser",
                "test@example.com",
                "password123",
                "Test User",
                "9876543210",
                "Example College",
                "I need access to perform computing jobs for my academic project."
        );

        when(userRepository.existsByUsername("testuser"))
                .thenReturn(false);

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("password123"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(AppUser.class)))
                .thenReturn(user);

        RegisterResponse response = authService.register(request);

        assertEquals(1L, response.id());
        assertEquals("testuser", response.username());
        assertEquals("test@example.com", response.email());

        verify(passwordEncoder).encode("password123");

        ArgumentCaptor<AppUser> captor =
                ArgumentCaptor.forClass(AppUser.class);

        verify(userRepository).save(captor.capture());

        captor.getValue().prePersist();

        assertEquals(
                com.minicloud.storage.StorageQuotaPolicy.DEFAULT_QUOTA_BYTES,
                captor.getValue().getStorageQuotaBytes()
        );

        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("Test User", captor.getValue().getFullName());
        assertEquals("9876543210", captor.getValue().getPhoneNumber());
        assertEquals("Example College", captor.getValue().getOrganization());

        assertEquals(
                "I need access to perform computing jobs for my academic project.",
                captor.getValue().getAccessReason()
        );
    }

    @Test
    void registerShouldRejectShortPassword() {

        RegisterRequest request = new RegisterRequest(
                "testuser",
                "test@example.com",
                "short"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void registerShouldRejectDuplicateUsername() {

        RegisterRequest request = new RegisterRequest(
                "testuser",
                "test@example.com",
                "password123",
                "Test User",
                "9876543210",
                "Example College",
                "I need access for my academic project."
        );

        when(userRepository.existsByUsername("testuser"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void registerShouldRejectDuplicateEmail() {

        RegisterRequest request = new RegisterRequest(
                "testuser",
                "test@example.com",
                "password123",
                "Test User",
                "9876543210",
                "Example College",
                "I need access for my academic project."
        );

        when(userRepository.existsByUsername("testuser"))
                .thenReturn(false);

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void loginShouldSucceedWithUniqueUserId() {

        LoginRequest request = new LoginRequest(
                "MC-ABCD2345",
                "password123"
        );

        when(userRepository.findByUniqueUserId("MC-ABCD2345"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encoded-password"
        )).thenReturn(true);

        when(jwtService.generateToken(
                1L,
                "testuser",
                "test@example.com",
                "USER"
        )).thenReturn("test.jwt.token");

        LoginResponse response = authService.login(request);

        assertEquals(1L, response.id());
        assertEquals("testuser", response.username());
        assertEquals("test@example.com", response.email());
        assertEquals("test.jwt.token", response.token());

        verify(userRepository).findByUniqueUserId("MC-ABCD2345");
        verify(passwordEncoder).matches(
                "password123",
                "encoded-password"
        );
    }

    @Test
    void loginShouldNormalizeUniqueUserIdToUppercase() {

        LoginRequest request = new LoginRequest(
                "mc-abcd2345",
                "password123"
        );

        when(userRepository.findByUniqueUserId("MC-ABCD2345"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encoded-password"
        )).thenReturn(true);

        when(jwtService.generateToken(
                1L,
                "testuser",
                "test@example.com",
                "USER"
        )).thenReturn("test.jwt.token");

        LoginResponse response = authService.login(request);

        assertEquals("test.jwt.token", response.token());

        verify(userRepository).findByUniqueUserId("MC-ABCD2345");
    }

    @Test
    void loginShouldRejectUnknownUser() {

        LoginRequest request = new LoginRequest(
                "MC-UNKNOWN1",
                "password123"
        );

        when(userRepository.findByUniqueUserId("MC-UNKNOWN1"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateToken(
                any(), any(), any(), any()
        );
    }

    @Test
    void loginShouldRejectIncorrectPassword() {

        LoginRequest request = new LoginRequest(
                "MC-ABCD2345",
                "wrongpassword"
        );

        when(userRepository.findByUniqueUserId("MC-ABCD2345"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrongpassword",
                "encoded-password"
        )).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verify(jwtService, never()).generateToken(
                any(), any(), any(), any()
        );
    }

    @Test
    void loginShouldRejectBlankIdentifier() {

        LoginRequest request = new LoginRequest(
                " ",
                "password123"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void loginShouldRejectBlankPassword() {

        LoginRequest request = new LoginRequest(
                "MC-ABCD2345",
                " "
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void loginShouldRejectPendingUser() {

        user.setStatus("PENDING");

        LoginRequest request = new LoginRequest(
                "MC-ABCD2345",
                "password123"
        );

        when(userRepository.findByUniqueUserId("MC-ABCD2345"))
                .thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(passwordEncoder, never()).matches(any(), any());

        verify(jwtService, never()).generateToken(
                any(), any(), any(), any()
        );
    }

    @Test
    void loginShouldRejectRejectedUser() {

        user.setStatus("REJECTED");

        LoginRequest request = new LoginRequest(
                "MC-ABCD2345",
                "password123"
        );

        when(userRepository.findByUniqueUserId("MC-ABCD2345"))
                .thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginShouldRejectSuspendedUser() {

        user.setStatus("SUSPENDED");

        LoginRequest request = new LoginRequest(
                "MC-ABCD2345",
                "password123"
        );

        when(userRepository.findByUniqueUserId("MC-ABCD2345"))
                .thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(passwordEncoder, never()).matches(any(), any());
    }
}
