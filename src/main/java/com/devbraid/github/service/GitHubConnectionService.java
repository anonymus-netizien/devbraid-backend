package com.devbraid.github.service;

import com.devbraid.github.client.GitHubApiClient;
import com.devbraid.github.dto.internal.RawGitHubBranch;
import com.devbraid.github.dto.internal.RawGitHubRepo;
import com.devbraid.github.dto.internal.RawGitHubUser;
import com.devbraid.github.dto.response.BranchDto;
import com.devbraid.github.dto.response.GitHubStatusResponse;
import com.devbraid.github.dto.response.GitRepositoryDto;
import com.devbraid.github.entity.GitHubConnection;
import com.devbraid.github.exception.GitHubAlreadyConnectedException;
import com.devbraid.github.exception.GitHubNotConnectedException;
import com.devbraid.github.exception.GitHubTokenExpiredException;
import com.devbraid.github.exception.GitHubTokenInvalidException;
import com.devbraid.github.repository.GitHubConnectionRepository;
import com.devbraid.github.util.PatEncryptor;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing GitHub PAT connections.
 * Handles connect, disconnect, validation, and repository/branch listing.
 * <p>
 * All exceptions propagate to GlobalExceptionHandler — no try-catches here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubConnectionService {

    private final GitHubConnectionRepository connectionRepository;
    private final GitHubApiClient gitHubApiClient;
    private final PatEncryptor patEncryptor;

    /**
     * Connect a GitHub account using a Personal Access Token.
     * Validates the token, encrypts it, and persists the connection.
     *
     * @param rawPat the plaintext GitHub PAT
     * @param user   the authenticated user
     * @return status response with connection details
     * @throws GitHubAlreadyConnectedException if already connected
     * @throws GitHubTokenInvalidException     if PAT is invalid
     */
    @Transactional
    public GitHubStatusResponse connect(String rawPat, User user) {
        if (connectionRepository.existsByUserId(user.getId())) {
            throw new GitHubAlreadyConnectedException(
                    "GitHub account already connected"
            );
        }

        // ponytail: validateToken exception propagates directly
        RawGitHubUser gitHubUser = gitHubApiClient.validateToken(rawPat);

        byte[] iv = patEncryptor.generateIv();
        byte[] encryptedPat = patEncryptor.encrypt(rawPat, iv);

        OffsetDateTime now = OffsetDateTime.now();
        GitHubConnection connection = GitHubConnection.builder()
                .user(user)
                .githubUsername(gitHubUser.getLogin())
                .encryptedPat(encryptedPat)
                .iv(iv)
                .connectedAt(now)
                .lastValidated(now)
                .build();

        connectionRepository.save(connection);

        log.info("GitHub connected for user {} (GitHub: {})", user.getId(), gitHubUser.getLogin());

        return GitHubStatusResponse.from(connection, true);
    }

    /**
     * Disconnect the GitHub account for the given user.
     *
     * @param user the authenticated user
     * @throws GitHubNotConnectedException if no connection exists
     */
    @Transactional
    public void disconnect(User user) {
        GitHubConnection connection = connectionRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new GitHubNotConnectedException("No GitHub connection found"));

        connectionRepository.delete(connection);
        log.info("GitHub disconnected for user {}", user.getId());
    }

    /**
     * Get the current GitHub connection status.
     * Validates the token eagerly so the frontend can show
     * "Token expired, reconnect" instead of discovering the failure later.
     *
     * @param user the authenticated user
     * @return status response (connected or disconnected)
     * @throws GitHubTokenExpiredException if the stored PAT is no longer valid
     */
    @Transactional(readOnly = true)
    public GitHubStatusResponse getStatus(User user) {
        Optional<GitHubConnection> connectionOpt = connectionRepository.findByUserId(user.getId());

        if (connectionOpt.isEmpty()) {
            return GitHubStatusResponse.disconnected();
        }

        GitHubConnection connection = connectionOpt.get();
        // ponytail: no try/catch — GitHubTokenInvalidException propagates to GlobalExceptionHandler (401)
        String decryptedPat = patEncryptor.decrypt(connection.getEncryptedPat(), connection.getIv());
        gitHubApiClient.validateToken(decryptedPat);
        return GitHubStatusResponse.from(connection, true);
    }

    /**
     * Validate GitHub connection on user login.
     * Returns connection status if connected, null otherwise.
     *
     * @param user the authenticated user
     * @return status response or null if not connected
     * @throws GitHubTokenExpiredException if the stored PAT is no longer valid
     */
    @Transactional(readOnly = true)
    public GitHubStatusResponse validateOnLogin(User user) {
        Optional<GitHubConnection> connectionOpt = connectionRepository.findByUserId(user.getId());

        if (connectionOpt.isEmpty()) {
            return null;
        }

        GitHubConnection connection = connectionOpt.get();
        // ponytail: no try/catch — GitHubTokenInvalidException propagates to GlobalExceptionHandler (401)
        String decryptedPat = patEncryptor.decrypt(connection.getEncryptedPat(), connection.getIv());
        gitHubApiClient.validateToken(decryptedPat);
        return GitHubStatusResponse.from(connection, true);
    }

    /**
     * List repositories accessible with the user's GitHub PAT.
     *
     * @param user the authenticated user
     * @return list of repository DTOs
     * @throws GitHubNotConnectedException if not connected
     */
    public List<GitRepositoryDto> listRepositories(User user) {
        GitHubConnection connection = getConnectionOrThrow(user);
        String decryptedPat = patEncryptor.decrypt(connection.getEncryptedPat(), connection.getIv());

        List<RawGitHubRepo> rawRepos = gitHubApiClient.listRepositories(decryptedPat);

        return rawRepos.stream()
                .map(r -> new GitRepositoryDto(r.getFullName(), r.getDefaultBranch(), r.isPrivate()))
                .toList();
    }

    /**
     * List branches for a specific repository.
     *
     * @param user  the authenticated user
     * @param owner repository owner
     * @param repo  repository name
     * @return list of branch DTOs
     * @throws GitHubNotConnectedException if not connected
     */
    public List<BranchDto> listBranches(User user, String owner, String repo) {
        GitHubConnection connection = getConnectionOrThrow(user);
        String decryptedPat = patEncryptor.decrypt(connection.getEncryptedPat(), connection.getIv());

        List<RawGitHubBranch> rawBranches = gitHubApiClient.listBranches(decryptedPat, owner, repo);

        return rawBranches.stream()
                .map(b -> new BranchDto(b.getName()))
                .toList();
    }

    /**
     * Get decrypted PAT for the given user — centralizes PAT decryption.
     * Eliminates direct GitHubConnectionRepository + PatEncryptor access from other modules.
     *
     * @param user the authenticated user
     * @return decrypted PAT string
     * @throws GitHubNotConnectedException if not connected
     */
    public String getDecryptedPatForUser(User user) {
        var connection = getConnectionOrThrow(user);
        return patEncryptor.decrypt(connection.getEncryptedPat(), connection.getIv());
    }

    // ── Private helpers ──────────────────────────────────────────────

    private GitHubConnection getConnectionOrThrow(User user) {
        return connectionRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new GitHubNotConnectedException("Connect GitHub first to access repositories."));
    }
}
