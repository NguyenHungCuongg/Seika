package com.seika.identity_service.dto.user_profile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileRequest {
    @NotBlank
    String userId;

    @NotBlank
    String fullName;

    @NotNull
    @Past
    LocalDate dateOfBirth;

    @NotBlank
    String gender;

    String profilePictureUrl;
}