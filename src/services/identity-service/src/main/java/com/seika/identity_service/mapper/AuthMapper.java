package com.seika.identity_service.mapper;

import com.seika.identity_service.dto.auth.AuthResponse;
import com.seika.identity_service.dto.auth.RegisterRequest;
import com.seika.identity_service.dto.auth.UserInfoResponse;
import com.seika.identity_service.entity.Role;
import com.seika.identity_service.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", source = "encodedPassword")
    @Mapping(target = "roles", source = "roles")
    User toUser(RegisterRequest request, String encodedPassword, Set<Role> roles);

    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "roles", expression = "java(mapRoleNames(user.getRoles()))")
    AuthResponse toAuthResponse(User user, String accessToken);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "roles", expression = "java(mapRoleNames(user.getRoles()))")
    UserInfoResponse toUserInfoResponse(User user);

    default Set<String> mapRoleNames(Set<Role> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }
}
