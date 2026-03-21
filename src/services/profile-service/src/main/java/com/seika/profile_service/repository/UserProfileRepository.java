package com.seika.profile_service.repository;

import com.seika.profile_service.enity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository  extends JpaRepository<UserProfile,String> {
	boolean existsByUserId(String userId);

	Optional<UserProfile> findByUserId(String userId);
}
