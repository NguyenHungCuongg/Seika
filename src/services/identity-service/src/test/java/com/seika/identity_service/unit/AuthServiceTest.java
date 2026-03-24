package com.seika.identity_service.unit;

import com.seika.identity_service.dto.auth.AuthResponse;
import com.seika.identity_service.dto.auth.LoginRequest;
import com.seika.identity_service.dto.auth.RegisterRequest;
import com.seika.identity_service.dto.user_profile.UserProfileRequest;
import com.seika.identity_service.entity.Role;
import com.seika.identity_service.entity.User;
import com.seika.identity_service.mapper.AuthMapper;
import com.seika.identity_service.mapper.ProfileMapper;
import com.seika.identity_service.repository.RoleRepository;
import com.seika.identity_service.repository.UserRepository;
import com.seika.identity_service.repository.httpclient.ProfileClient;
import com.seika.identity_service.service.AuthService;
import com.seika.identity_service.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private ProfileClient profileClient;

    @Mock
    private ProfileMapper profileMapper;

    @Mock
    private AuthMapper authMapper;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest mockRegisterRequest;
    private User mockUser;
    private Role mockRole;

    @BeforeEach
    void setUp() {
        mockRegisterRequest = new RegisterRequest();
        mockRegisterRequest.setUsername("test_student_1");
        mockRegisterRequest.setPassword("123456");
        mockRegisterRequest.setRole("STUDENT");
        mockRegisterRequest.setFullName("Nguyen Van Test");
        mockRegisterRequest.setDateOfBirth(LocalDate.of(1980, 1, 1));
        mockRegisterRequest.setGender("Female");
        mockRegisterRequest.setProfilePictureUrl("example.png");

        mockRole = new Role();
        mockRole.setName("STUDENT");

        mockUser = new User();
        mockUser.setId("TEST01");
        mockUser.setUsername("test_student_1");
        mockUser.setPassword("123456");
        mockUser.setRoles(Set.of(mockRole));
    }

    @Test
    void register_success() {
        // Arrange
        when(userRepository.existsByUsername("test_student_1")).thenReturn(false);
        when(roleRepository.findById("STUDENT")).thenReturn(Optional.of(mockRole));
        when(passwordEncoder.encode("123456")).thenReturn("encoded");

        when(authMapper.toUser(any(), eq("encoded"), any())).thenReturn(mockUser);
        when(userRepository.save(any())).thenReturn(mockUser);

        UserProfileRequest userProfileRequest = new UserProfileRequest();
        when(profileMapper.toUserProfileRequest(any(), anyString())).thenReturn(userProfileRequest);

        when(jwtService.generateAccessToken(any())).thenReturn("accessToken");

        AuthResponse response = new AuthResponse();
        when(authMapper.toAuthResponse(mockUser, "accessToken")).thenReturn(response);

        // Act
        AuthResponse result = authService.register(mockRegisterRequest);

        // Assert
        assertNotNull(result);
        verify(userRepository).save(any());
        verify(profileClient).createProfile(userProfileRequest);
        verify(jwtService).generateAccessToken(any());
    }

    @Test
    void register_usernameAlreadyExists() {
        when(userRepository.existsByUsername("test_student_1")).thenReturn(true);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> authService.register(mockRegisterRequest));
        assertEquals("Username already exists", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_invalidRole() {
        RegisterRequest invalidRoleRequest = new RegisterRequest();
        invalidRoleRequest.setUsername("test_student_2");
        invalidRoleRequest.setPassword("123456");
        invalidRoleRequest.setRole("ADMIN");
        invalidRoleRequest.setFullName("Nguyen Van Test");
        invalidRoleRequest.setDateOfBirth(LocalDate.of(1980, 1, 1));
        invalidRoleRequest.setGender("Female");
        invalidRoleRequest.setProfilePictureUrl("example.png");

        when(userRepository.existsByUsername("test_student_2")).thenReturn(false);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> authService.register(invalidRoleRequest));
        assertEquals("Only STUDENT or TEACHER can be selected during registration", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success() {
        // Arrange
        Authentication authentication = new UsernamePasswordAuthenticationToken("test_student_1", null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByUsername("test_student_1")).thenReturn(Optional.of(mockUser));
        when(jwtService.generateAccessToken(authentication)).thenReturn("accessToken");
        AuthResponse response = new AuthResponse();
        when(authMapper.toAuthResponse(mockUser, "accessToken")).thenReturn(response);

        // Act
        AuthResponse result = authService.login(new com.seika.identity_service.dto.auth.LoginRequest("test_student_1", "123456"));

        // Assert
        assertNotNull(result);
        verify(authenticationManager).authenticate(any());
        verify(userRepository).findByUsername("test_student_1");
        verify(jwtService).generateAccessToken(authentication);
    }

    @Test
    void login_userNotFound() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("test_student_1", null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByUsername("test_student_1")).thenReturn(Optional.empty());

        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.login(new com.seika.identity_service.dto.auth.LoginRequest("test_student_1", "123456")));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void me_success() {
        // Arrange
        org.springframework.security.core.context.SecurityContext securityContext = mock(org.springframework.security.core.context.SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(new UsernamePasswordAuthenticationToken("test_student_1", null));
        org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
        when(userRepository.findByUsername("test_student_1")).thenReturn(Optional.of(mockUser));
        com.seika.identity_service.dto.auth.UserInfoResponse userInfoResponse =
                new com.seika.identity_service.dto.auth.UserInfoResponse("test_student_1", "Nguyen Van Test", Set.of("STUDENT"));
        when(authMapper.toUserInfoResponse(mockUser)).thenReturn(userInfoResponse);

        // Act
        com.seika.identity_service.dto.auth.UserInfoResponse result = authService.me();

        // Assert
        assertNotNull(result);
        verify(userRepository).findByUsername("test_student_1");
        verify(authMapper).toUserInfoResponse(mockUser);
    }
}
