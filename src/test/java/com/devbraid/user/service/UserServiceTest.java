package com.devbraid.user.service;

import com.devbraid.user.dto.request.UpdateProfileRequest;
import com.devbraid.user.dto.response.UserProfileResponse;
import com.devbraid.user.entity.User;
import com.devbraid.user.exception.UserNotFoundException;
import com.devbraid.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UserService (Clerk-backed)")
class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String CLERK_ID = "user_2abc123";
    private static final String EMAIL = "john@example.com";
    private static final String FULL_NAME = "John Doe";

    private UserRepository userRepository;
    private ModelMapper generalModelMapper;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        generalModelMapper = new ModelMapper();
        userService = new UserService(userRepository, generalModelMapper);
    }

    @Test
    @DisplayName("syncClerkUser returns the existing user when the clerk id is already linked")
    void syncClerkUser_ExistingUser_ReturnsIt() {
        User existing = User.builder().id(USER_ID).clerkId(CLERK_ID).email(EMAIL).fullName(FULL_NAME).build();
        when(userRepository.findByClerkId(CLERK_ID)).thenReturn(Optional.of(existing));

        User result = userService.syncClerkUser(CLERK_ID, EMAIL, FULL_NAME);

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncClerkUser creates a local user on first request from a Clerk session")
    void syncClerkUser_FirstRequest_CreatesUser() {
        when(userRepository.findByClerkId(CLERK_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.syncClerkUser(CLERK_ID, EMAIL, FULL_NAME);

        assertThat(result.getClerkId()).isEqualTo(CLERK_ID);
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getFullName()).isEqualTo(FULL_NAME);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("updateProfile updates the full name and returns the fresh profile")
    void updateProfile_WithFullName_UpdatesAndReturns() {
        User user = User.builder().id(USER_ID).clerkId(CLERK_ID).email(EMAIL).fullName("Old Name").build();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.updateProfile(user, new UpdateProfileRequest("Jane Roe"));

        assertThat(user.getFullName()).isEqualTo("Jane Roe");
        assertThat(response.getFullName()).isEqualTo("Jane Roe");
        assertThat(response.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("updateProfile with a blank full name leaves the user untouched")
    void updateProfile_BlankFullName_LeavesUserUntouched() {
        User user = User.builder().id(USER_ID).clerkId(CLERK_ID).email(EMAIL).fullName(FULL_NAME).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.updateProfile(user, new UpdateProfileRequest("  "));

        assertThat(user.getFullName()).isEqualTo(FULL_NAME);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("getUserProfile returns the mapped profile with DEVELOPER role")
    void getUserProfile_ReturnsMappedProfile() {
        User user = User.builder().id(USER_ID).clerkId(CLERK_ID).email(EMAIL).fullName(FULL_NAME).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getUserProfile(USER_ID.toString());

        assertThat(response.getId()).isEqualTo(USER_ID);
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getRole()).isEqualTo("DEVELOPER");
    }

    @Test
    @DisplayName("getUserProfile throws UserNotFoundException when the user does not exist")
    void getUserProfile_UnknownUser_Throws() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile(USER_ID.toString()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
