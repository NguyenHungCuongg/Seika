package com.seika.identity_service.service;

import com.seika.identity_service.dto.auth.AuthResponse;
import com.seika.identity_service.dto.auth.LoginRequest;
import com.seika.identity_service.dto.auth.RegisterRequest;
import com.seika.identity_service.dto.auth.UserInfoResponse;
import com.seika.identity_service.dto.user_profile.UserProfileRequest;
import com.seika.identity_service.entity.Role;
import com.seika.identity_service.entity.User;
import com.seika.identity_service.mapper.AuthMapper;
import com.seika.identity_service.mapper.ProfileMapper;
import com.seika.identity_service.repository.RoleRepository;
import com.seika.identity_service.repository.UserRepository;
import com.seika.identity_service.repository.httpclient.ProfileClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final Set<String> SELF_SELECTABLE_ROLES = Set.of(ROLE_STUDENT, ROLE_TEACHER);

 
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ProfileClient profileClient;
    private final ProfileMapper profileMapper;
    private final AuthMapper authMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Registration failed: Username {} already exists", request.getUsername());
            throw new IllegalArgumentException("Username already exists");
        }

        Role selectedRole = resolveSelfSelectableRole(request.getRole());

        User user = authMapper.toUser(request, passwordEncoder.encode(request.getPassword()), Set.of(selectedRole));
        User savedUser = userRepository.save(user);
        log.info("User registered with username={}, role={}", user.getUsername(), selectedRole.getName());

        UserProfileRequest userProfileRequest = profileMapper.toUserProfileRequest(request, savedUser.getId());
        try {
            profileClient.createProfile(userProfileRequest);
            log.info("Profile created for userId={}", savedUser.getId());
        } catch (FeignException exception) {
            log.error("Profile creation failed for userId={} with status={}", savedUser.getId(), exception.status());
            throw new IllegalStateException("Could not create user profile. Registration rolled back.");
        }
        
        List<GrantedAuthority> authorities = savedUser.getRoles().stream()
            .map(Role::getName)
            .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
            .collect(Collectors.toList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(savedUser.getUsername(), null, authorities);
        String accessToken = jwtService.generateAccessToken(authentication);

        return authMapper.toAuthResponse(savedUser, accessToken);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String accessToken = jwtService.generateAccessToken(authentication);
        log.info("User logged in: username={}, roles={}", user.getUsername(), authMapper.mapRoleNames(user.getRoles()));
        return authMapper.toAuthResponse(user, accessToken);
    }

    public UserInfoResponse me() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return authMapper.toUserInfoResponse(user);
    }

    private Role resolveSelfSelectableRole(String rawRole) {
        String normalizedRole = rawRole == null ? "" : rawRole.trim().toUpperCase(Locale.ROOT);
        if (!SELF_SELECTABLE_ROLES.contains(normalizedRole)) {
            log.warn("Registration failed: Invalid role attempted - {}", rawRole);
            throw new IllegalArgumentException("Only STUDENT or TEACHER can be selected during registration");
        }

        return roleRepository.findById(normalizedRole)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + normalizedRole));
    }
}
