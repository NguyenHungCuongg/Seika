package com.seika.identity_service.repository.httpclient;

import com.seika.identity_service.dto.user_profile.UserProfileRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProfileClientFallback implements ProfileClient {
    @Override
    public void createProfile(UserProfileRequest userProfileRequest) {
        log.error("Profile service is unavailable. Circuit breaker opened or call failed. Could not create profile for user: {}", userProfileRequest.getUserId());
        throw new RuntimeException("Profile service is currently unavailable. Please try again later.");
    }
}
