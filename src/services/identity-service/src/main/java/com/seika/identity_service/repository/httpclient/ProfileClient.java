package com.seika.identity_service.repository.httpclient;

import com.seika.identity_service.dto.user_profile.UserProfileRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {
    @PostMapping(value = "/api/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
    void createProfile(@RequestBody UserProfileRequest userProfileRequest);
}
