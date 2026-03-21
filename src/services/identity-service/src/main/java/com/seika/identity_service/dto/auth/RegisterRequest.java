package com.seika.identity_service.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterRequest {
    @NotBlank
    String username;

    @NotBlank
    String password;

    @NotBlank
    @Pattern(regexp = "(?i)STUDENT|TEACHER", message = "role must be STUDENT or TEACHER")
    String role;
}
