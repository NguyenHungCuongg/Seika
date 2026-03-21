package com.seika.identity_service.dto.auth;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthResponse {
    String accessToken;
    String tokenType;
    String username;
    Set<String> roles;
}
