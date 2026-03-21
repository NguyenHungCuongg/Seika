package com.seika.profile_service.enity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Entity
@Table(name = "user_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String userId;
    String fullName;
    LocalDate dateOfBirth;
    String gender;
    String profilePictureUrl;
}
