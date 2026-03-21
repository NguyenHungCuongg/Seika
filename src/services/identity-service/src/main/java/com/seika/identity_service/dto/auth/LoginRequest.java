package com.seika.identity_service.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginRequest {
    @NotBlank
    String username;

    @NotBlank
    String password;
}
