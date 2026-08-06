package com.devbraid.brief.controller;

import com.devbraid.brief.dto.BriefListItemResponse;
import com.devbraid.brief.dto.BriefResponse;
import com.devbraid.brief.service.BriefBuilderService;
import com.devbraid.common.ApiResponse;
import com.devbraid.common.api.ApiErrorResponses;
import com.devbraid.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/briefs")
@RequiredArgsConstructor
@Tag(name = "Change Briefs", description = "Generated Markdown change briefs. Generation and publishing happen on the thread resource; these endpoints list and fetch briefs directly.")
@SecurityRequirement(name = "bearer-jwt")
public class ChangeBriefController {

    private final BriefBuilderService briefBuilderService;

    @GetMapping
    @Operation(
            summary = "List change briefs",
            description = "Paginated list of the authenticated user's generated briefs."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Briefs retrieved",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<Page<BriefListItemResponse>>> listBriefs(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<BriefListItemResponse> briefs = briefBuilderService.listBriefsByUser(user, pageable);
        return ResponseEntity.ok(ApiResponse.success("Briefs retrieved", briefs));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a change brief",
            description = "Returns a brief by its ID, including the full Markdown content."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Brief retrieved",
            content = @Content(schema = @Schema(implementation = BriefResponse.class)))
    @ApiErrorResponses
    public ResponseEntity<ApiResponse<BriefResponse>> getBrief(
            @PathVariable @Parameter(description = "Brief ID") UUID id,
            @AuthenticationPrincipal User user) {
        BriefResponse response = briefBuilderService.getBriefById(user, id);
        return ResponseEntity.ok(ApiResponse.success("Brief retrieved", response));
    }
}
