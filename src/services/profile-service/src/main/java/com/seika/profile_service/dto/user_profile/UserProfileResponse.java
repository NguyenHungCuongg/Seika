package com.seika.profile_service.dto.user_profile;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponse {
	String id;
	String userId;
	String fullName;
	LocalDate dateOfBirth;
	String gender;
	String profilePictureUrl;
}
