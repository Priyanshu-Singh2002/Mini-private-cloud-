
package com.minicloud.service;

import com.minicloud.security.JwtService;

import com.minicloud.dto.LoginRequest;
import com.minicloud.dto.LoginResponse;
import com.minicloud.dto.RegisterRequest;
import com.minicloud.dto.RegisterResponse;
import com.minicloud.entity.AppUser;
import com.minicloud.repository.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

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
                user.setPasswordHash("encoded-password");
        }

        @Test
        void registerShouldSaveUserWithEncodedPassword() {
                RegisterRequest request = new RegisterRequest(
                                "testuser", "test@example.com", "password123");

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
                verify(userRepository).save(any(AppUser.class));
        }

        @Test
        void registerShouldRejectShortPassword() {
                RegisterRequest request = new RegisterRequest(
                                "testuser", "test@example.com", "short");

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> authService.register(request));

                assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                verifyNoInteractions(userRepository, passwordEncoder);
        }

        @Test
        void registerShouldRejectDuplicateUsername() {
                RegisterRequest request = new RegisterRequest(
                                "testuser", "test@example.com", "password123");

                when(userRepository.existsByUsername("testuser"))
                                .thenReturn(true);

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> authService.register(request));

                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                verify(userRepository, never()).save(any(AppUser.class));
        }

        @Test
        void registerShouldRejectDuplicateEmail() {
                RegisterRequest request = new RegisterRequest(
                                "testuser", "test@example.com", "password123");

                when(userRepository.existsByUsername("testuser"))
                                .thenReturn(false);
                when(userRepository.existsByEmail("test@example.com"))
                                .thenReturn(true);

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> authService.register(request));

                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                verify(userRepository, never()).save(any(AppUser.class));
        }

        @Test
        void loginShouldSucceedWithUsername() {
                LoginRequest request = new LoginRequest(
                                "testuser", "password123");

                when(userRepository.findByUsernameOrEmail(
                                "testuser", "testuser"))
                                .thenReturn(Optional.of(user));
                when(passwordEncoder.matches(
                                "password123", "encoded-password"))
                                .thenReturn(true);

                when(jwtService.generateToken(
                                1L,
                                "testuser",
                                "test@example.com",
                                "USER")).thenReturn("test.jwt.token");

                LoginResponse response = authService.login(request);

                assertEquals(1L, response.id());
                assertEquals("testuser", response.username());
                assertEquals("test@example.com", response.email());
        }

        @Test
        void loginShouldSucceedWithEmailIgnoringCase() {
                LoginRequest request = new LoginRequest(
                                "TEST@example.com", "password123");

                when(userRepository.findByUsernameOrEmail(
                                "TEST@example.com", "test@example.com"))
                                .thenReturn(Optional.of(user));
                when(passwordEncoder.matches(
                                "password123", "encoded-password"))
                                .thenReturn(true);

                when(jwtService.generateToken(
                                1L,
                                "testuser",
                                "test@example.com",
                                "USER")).thenReturn("test.jwt.token");

                LoginResponse response = authService.login(request);

                assertEquals("testuser", response.username());
                assertEquals("test.jwt.token", response.token());
        }

        @Test
        void loginShouldRejectUnknownUser() {
                LoginRequest request = new LoginRequest(
                                "unknown", "password123");

                when(userRepository.findByUsernameOrEmail(
                                "unknown", "unknown"))
                                .thenReturn(Optional.empty());

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> authService.login(request));

                assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
                verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        void loginShouldRejectIncorrectPassword() {
                LoginRequest request = new LoginRequest(
                                "testuser", "wrongpassword");

                when(userRepository.findByUsernameOrEmail(
                                "testuser", "testuser"))
                                .thenReturn(Optional.of(user));
                when(passwordEncoder.matches(
                                "wrongpassword", "encoded-password"))
                                .thenReturn(false);

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> authService.login(request));

                assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        }

        @Test
        void loginShouldRejectBlankIdentifier() {
                LoginRequest request = new LoginRequest(
                                " ", "password123");

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> authService.login(request));

                assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                verifyNoInteractions(userRepository, passwordEncoder);
        }
}
