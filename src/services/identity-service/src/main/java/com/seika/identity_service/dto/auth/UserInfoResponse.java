package com.seika.identity_service.dto.auth;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class UserInfoResponse {
    private String id;
    private String username;
    private Set<String> roles;
}
