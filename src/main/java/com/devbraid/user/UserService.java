package com.devbraid.user;

import com.devbraid.security.JwtTokenProvider;
import com.devbraid.user.dto.LoginResponse;
import com.devbraid.user.dto.SignupRequest;
import com.devbraid.user.exception.InvalidCredentialsException;
import com.devbraid.user.exception.UserAlreadyExistsException;
import com.devbraid.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public void signup(SignupRequest request) {
        log.info("UserService :: Signup request for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);

        log.info("UserService :: User created for email: {}", request.getEmail());
    }

    public LoginResponse login(String email, String password) {
        log.info("UserService :: Login request for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found for email: " + email));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String userId = user.getId().toString();
        String userRole = "DEVELOPER";

        String accessToken = jwtTokenProvider.createAccessToken(userId, user.getEmail(), userRole);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, user.getEmail(), userRole);

        log.info("UserService :: User logged in with id: {}", user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .issuedAt(Instant.now())
                .expiresAt(jwtTokenProvider.getAccessExpiresAt())
                .email(user.getEmail())
                .userId(user.getId())
                .role(userRole)
                .build();
    }
}
