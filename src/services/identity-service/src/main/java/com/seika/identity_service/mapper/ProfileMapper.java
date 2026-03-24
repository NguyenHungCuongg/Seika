package com.seika.identity_service.mapper;

import com.seika.identity_service.dto.auth.RegisterRequest;
import com.seika.identity_service.dto.user_profile.UserProfileRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProfileMapper {

	@Mapping(target = "userId", source = "userId")
	UserProfileRequest toUserProfileRequest(RegisterRequest request, String userId);
}
