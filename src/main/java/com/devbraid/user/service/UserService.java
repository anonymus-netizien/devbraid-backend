package com.devbraid.user.service;

import com.devbraid.user.dto.request.UpdateProfileRequest;
import com.devbraid.user.dto.response.UserProfileResponse;
import com.devbraid.user.entity.User;
import com.devbraid.user.exception.UserNotFoundException;
import com.devbraid.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ModelMapper generalModelMapper;

    /**
     * Returns the local user for a Clerk session. First request for a Clerk user
     * creates the local row (clerk_id is the link); subsequent requests reuse it.
     */
    public User syncClerkUser(String clerkId, String email, String fullName) {
        return userRepository.findByClerkId(clerkId)
                .orElseGet(() -> {
                    User user = User.builder()
                            .clerkId(clerkId)
                            .email(email)
                            .fullName(fullName)
                            .build();
                    userRepository.save(user);
                    log.info("UserService :: Created user {} for clerk {}", user.getId(), clerkId);
                    return user;
                });
    }

    public UserProfileResponse updateProfile(User user, UpdateProfileRequest request) {
        log.info("UserService :: Update profile for user {}", user.getEmail());

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
            userRepository.save(user);
            log.info("UserService :: Profile updated for user {}", user.getId());
        }

        return getUserProfile(user.getId().toString());
    }

    public UserProfileResponse getUserProfile(String userId) {
        log.info("UserService :: Get user profile for id: {}", userId);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        UserProfileResponse response = generalModelMapper.map(user, UserProfileResponse.class);
        response.setRole("DEVELOPER");
        return response;
    }
}
