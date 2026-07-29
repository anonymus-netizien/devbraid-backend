package com.devbraid.brief.controller;

import com.devbraid.brief.dto.BriefListItemResponse;
import com.devbraid.brief.dto.BriefResponse;
import com.devbraid.brief.service.BriefBuilderService;
import com.devbraid.common.ApiResponse;
import com.devbraid.user.entity.User;
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
public class ChangeBriefController {

    private final BriefBuilderService briefBuilderService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<BriefListItemResponse>>> listBriefs(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        Page<BriefListItemResponse> briefs = briefBuilderService.listBriefsByUser(user, pageable);
        return ResponseEntity.ok(ApiResponse.success("Briefs retrieved", briefs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BriefResponse>> getBrief(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        BriefResponse response = briefBuilderService.getBriefById(user, id);
        return ResponseEntity.ok(ApiResponse.success("Brief retrieved", response));
    }
}
