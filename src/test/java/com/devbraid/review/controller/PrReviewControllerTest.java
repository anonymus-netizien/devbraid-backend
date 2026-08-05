package com.devbraid.review.controller;

import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.common.exception.GlobalExceptionHandler;
import com.devbraid.review.dto.response.PrReviewResponse;
import com.devbraid.review.entity.ReviewStatus;
import com.devbraid.review.service.PrReviewService;
import com.devbraid.review.service.PrReviewTriggerService;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PrReviewController Unit Tests")
@ExtendWith(MockitoExtension.class)
class PrReviewControllerTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID REVIEW_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440003");

    private MockMvc mockMvc;
    @Mock
    private PrReviewService prReviewService;
    @Mock
    private PrReviewTriggerService prReviewTriggerService;
    private PrReviewController controller;
    private User testUser;

    @BeforeEach
    void setUp() {
        controller = new PrReviewController(prReviewService, prReviewTriggerService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        testUser = User.builder()
                .id(USER_ID)
                .fullName("Test User")
                .email("test@example.com")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(testUser, null, "ROLE_DEVELOPER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/reviews/{reviewId}/publish returns 200 with the PUBLISHED review")
    void publishReview_Returns200() throws Exception {
        PrReviewResponse response = PrReviewResponse.builder()
                .id(REVIEW_ID)
                .threadId(UUID.randomUUID())
                .prNumber(42)
                .headSha("abc123")
                .status(ReviewStatus.PUBLISHED)
                .published(true)
                .githubReviewUrl("https://github.com/octo/repo/pull/42#pullrequestreview-123")
                .build();
        when(prReviewService.publishReview(testUser, REVIEW_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/reviews/{reviewId}/publish", REVIEW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Review published"))
                .andExpect(jsonPath("$.data.id").value(REVIEW_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.published").value(true))
                .andExpect(jsonPath("$.data.githubReviewUrl").value("https://github.com/octo/repo/pull/42#pullrequestreview-123"));

        verify(prReviewService).publishReview(testUser, REVIEW_ID);
    }

    @Test
    @DisplayName("POST /api/v1/reviews/{reviewId}/publish returns 403 when the review is not owned by the caller")
    void publishReview_Unauthorized_Returns403() throws Exception {
        when(prReviewService.publishReview(testUser, REVIEW_ID))
                .thenThrow(new AccessDeniedException("You do not own this review"));

        mockMvc.perform(post("/api/v1/reviews/{reviewId}/publish", REVIEW_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/reviews/{reviewId}/publish returns 400 when the review is not COMPLETED")
    void publishReview_WrongStatus_Returns400() throws Exception {
        when(prReviewService.publishReview(testUser, REVIEW_ID))
                .thenThrow(new IllegalArgumentException("Only COMPLETED reviews can be published"));

        mockMvc.perform(post("/api/v1/reviews/{reviewId}/publish", REVIEW_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Only COMPLETED reviews can be published"));
    }

    @Test
    @DisplayName("POST /api/v1/reviews/{reviewId}/publish returns 404 for a missing review")
    void publishReview_Missing_Returns404() throws Exception {
        when(prReviewService.publishReview(testUser, REVIEW_ID))
                .thenThrow(new ThreadNotFoundException("Review not found"));

        mockMvc.perform(post("/api/v1/reviews/{reviewId}/publish", REVIEW_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Review not found"));
    }
}
