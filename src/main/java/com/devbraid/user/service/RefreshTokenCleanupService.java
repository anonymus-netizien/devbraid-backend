package com.devbraid.user.service;

import com.devbraid.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    // ponytail: @Transactional lives on the repo method, not here — single call doesn't need wrapping
    @Scheduled(cron = "0 0 3 * * ?") // daily at 3am
    public void purgeExpiredAndRevokedTokens() {
        int deleted = refreshTokenRepository.deleteByExpiresAtBeforeOrRevokedTrue(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("RefreshTokenCleanup :: Purged {} expired/revoked refresh tokens", deleted);
        }
    }
}
