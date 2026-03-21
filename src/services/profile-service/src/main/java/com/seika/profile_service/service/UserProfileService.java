package com.seika.profile_service.service;

import com.seika.profile_service.dto.user_profile.UserProfileRequest;
import com.seika.profile_service.dto.user_profile.UserProfileResponse;
import com.seika.profile_service.enity.UserProfile;
import com.seika.profile_service.exception.ConflictException;
import com.seika.profile_service.exception.ResourceNotFoundException;
import com.seika.profile_service.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public UserProfileResponse createUserProfile(UserProfileRequest request) {
        if (userProfileRepository.existsByUserId(request.getUserId())) {
            throw new ConflictException("Profile already exists for userId: " + request.getUserId());
        }

        UserProfile userProfile = UserProfile.builder()
            .userId(request.getUserId())
            .fullName(request.getFullName())
            .dateOfBirth(request.getDateOfBirth())
            .gender(request.getGender())
            .profilePictureUrl(request.getProfilePictureUrl())
            .build();

        UserProfile savedProfile = userProfileRepository.save(userProfile);
        log.info("Created profile for userId={}", savedProfile.getUserId());
        return toResponse(savedProfile);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileByUserId(String userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for userId: " + userId));
        return toResponse(profile);
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
                .dateOfBirth(profile.getDateOfBirth())
                .gender(profile.getGender())
                .profilePictureUrl(profile.getProfilePictureUrl())
                .build();
    }
}
