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
import com.devbraid.github.exception.GitHubTokenInvalidException;
import com.devbraid.github.repository.GitHubConnectionRepository;
import com.devbraid.github.util.PatEncryptor;
import com.devbraid.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("GitHubConnectionService Unit Tests")
@ExtendWith(MockitoExtension.class)
class GitHubConnectionServiceTest {

    private static final String RAW_PAT = "ghp_testToken123";
    private static final byte[] ENCRYPTED_PAT = "encrypted-pat-value".getBytes();
    private static final byte[] IV = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    private static final String GITHUB_USERNAME = "testuser";
    private static final String OWNER = "testowner";
    private static final String REPO = "testrepo";
    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    @Mock
    private GitHubConnectionRepository connectionRepository;
    @Mock
    private GitHubApiClient gitHubApiClient;
    @Mock
    private PatEncryptor patEncryptor;
    private GitHubConnectionService service;
    private User testUser;
    private GitHubConnection testConnection;

    @BeforeEach
    void setUp() {
        service = new GitHubConnectionService(connectionRepository, gitHubApiClient, patEncryptor);

        testUser = User.builder()
                .id(USER_ID)
                .fullName("Test User")
                .email("test@example.com")
                .build();

        testConnection = GitHubConnection.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .githubUsername(GITHUB_USERNAME)
                .encryptedPat(ENCRYPTED_PAT)
                .iv(IV)
                .build();
    }

    @Test
    @DisplayName("connect() should create new connection when valid PAT provided")
    void connect_ValidPat_CreatesConnection() {
        RawGitHubUser gitHubUser = new RawGitHubUser();
        gitHubUser.setLogin(GITHUB_USERNAME);

        when(connectionRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(gitHubApiClient.validateToken(RAW_PAT)).thenReturn(gitHubUser);
        when(patEncryptor.generateIv()).thenReturn(IV);
        when(patEncryptor.encrypt(RAW_PAT, IV)).thenReturn(ENCRYPTED_PAT);
        when(connectionRepository.save(any(GitHubConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        GitHubStatusResponse response = service.connect(RAW_PAT, testUser);

        assertThat(response.isConnected()).isTrue();
        assertThat(response.isValid()).isTrue();
        assertThat(response.getGithubUsername()).isEqualTo(GITHUB_USERNAME);

        verify(connectionRepository).existsByUserId(USER_ID);
        verify(gitHubApiClient).validateToken(RAW_PAT);
        verify(patEncryptor).generateIv();
        verify(patEncryptor).encrypt(RAW_PAT, IV);
        verify(connectionRepository).save(any(GitHubConnection.class));
    }

    @Test
    @DisplayName("connect() should throw when already connected")
    void connect_AlreadyConnected_ThrowsException() {
        when(connectionRepository.existsByUserId(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.connect(RAW_PAT, testUser))
                .isInstanceOf(GitHubAlreadyConnectedException.class)
                .hasMessageContaining("already connected");

        verify(connectionRepository).existsByUserId(USER_ID);
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("connect() should throw when PAT is invalid")
    void connect_InvalidPat_ThrowsException() {
        when(connectionRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(gitHubApiClient.validateToken(RAW_PAT))
                .thenThrow(new GitHubTokenInvalidException("Bad credentials"));

        assertThatThrownBy(() -> service.connect(RAW_PAT, testUser))
                .isInstanceOf(GitHubTokenInvalidException.class);

        verify(connectionRepository).existsByUserId(USER_ID);
        verify(gitHubApiClient).validateToken(RAW_PAT);
        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("disconnect() should delete existing connection")
    void disconnect_ExistingConnection_Deletes() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testConnection));

        service.disconnect(testUser);

        verify(connectionRepository).findByUserId(USER_ID);
        verify(connectionRepository).delete(testConnection);
    }

    @Test
    @DisplayName("disconnect() should throw when not connected")
    void disconnect_NotConnected_ThrowsException() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect(testUser))
                .isInstanceOf(GitHubNotConnectedException.class)
                .hasMessageContaining("No GitHub connection found");

        verify(connectionRepository).findByUserId(USER_ID);
    }

    @Test
    @DisplayName("getStatus() should return disconnected when no connection exists")
    void getStatus_NoConnection_ReturnsDisconnected() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        GitHubStatusResponse response = service.getStatus(testUser);

        assertThat(response.isConnected()).isFalse();
        assertThat(response.isValid()).isFalse();

        verify(connectionRepository).findByUserId(USER_ID);
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("getStatus() should return connected when token is valid")
    void getStatus_Connected_ReturnsConnected() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testConnection));
        when(patEncryptor.decrypt(ENCRYPTED_PAT, IV)).thenReturn(RAW_PAT);
        when(gitHubApiClient.validateToken(RAW_PAT)).thenReturn(new RawGitHubUser());

        GitHubStatusResponse response = service.getStatus(testUser);

        assertThat(response.isConnected()).isTrue();
        assertThat(response.isValid()).isTrue();
        assertThat(response.getGithubUsername()).isEqualTo(GITHUB_USERNAME);

        verify(connectionRepository).findByUserId(USER_ID);
        verify(patEncryptor).decrypt(ENCRYPTED_PAT, IV);
        verify(gitHubApiClient).validateToken(RAW_PAT);
    }

    @Test
    @DisplayName("getStatus() should throw GitHubTokenExpiredException when token is invalid")
    void getStatus_InvalidToken_ThrowsException() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testConnection));
        when(patEncryptor.decrypt(ENCRYPTED_PAT, IV)).thenReturn(RAW_PAT);
        when(gitHubApiClient.validateToken(RAW_PAT))
                .thenThrow(new GitHubTokenInvalidException("Bad credentials"));

        assertThatThrownBy(() -> service.getStatus(testUser))
                .isInstanceOf(GitHubTokenInvalidException.class)
                .hasMessageContaining("Bad credentials");
    }

    @Test
    @DisplayName("validateOnLogin() should return null when not connected")
    void validateOnLogin_NotConnected_ReturnsNull() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        GitHubStatusResponse response = service.validateOnLogin(testUser);

        assertThat(response).isNull();
        verify(connectionRepository).findByUserId(USER_ID);
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("validateOnLogin() should return connected when token is valid")
    void validateOnLogin_Connected_ReturnsConnected() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testConnection));
        when(patEncryptor.decrypt(ENCRYPTED_PAT, IV)).thenReturn(RAW_PAT);
        when(gitHubApiClient.validateToken(RAW_PAT)).thenReturn(new RawGitHubUser());

        GitHubStatusResponse response = service.validateOnLogin(testUser);

        assertThat(response).isNotNull();
        assertThat(response.isConnected()).isTrue();
        assertThat(response.isValid()).isTrue();
        assertThat(response.getGithubUsername()).isEqualTo(GITHUB_USERNAME);

        verify(connectionRepository).findByUserId(USER_ID);
        verify(patEncryptor).decrypt(ENCRYPTED_PAT, IV);
        verify(gitHubApiClient).validateToken(RAW_PAT);
    }

    @Test
    @DisplayName("validateOnLogin() should throw GitHubTokenExpiredException when token is invalid")
    void validateOnLogin_InvalidToken_ThrowsException() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testConnection));
        when(patEncryptor.decrypt(ENCRYPTED_PAT, IV)).thenReturn(RAW_PAT);
        when(gitHubApiClient.validateToken(RAW_PAT))
                .thenThrow(new GitHubTokenInvalidException("Bad credentials"));

        assertThatThrownBy(() -> service.validateOnLogin(testUser))
                .isInstanceOf(GitHubTokenInvalidException.class)
                .hasMessageContaining("Bad credentials");
    }

    @Test
    @DisplayName("listRepositories() should return mapped repository list")
    void listRepositories_Connected_ReturnsList() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testConnection));
        when(patEncryptor.decrypt(ENCRYPTED_PAT, IV)).thenReturn(RAW_PAT);

        RawGitHubRepo rawRepo = new RawGitHubRepo();
        rawRepo.setFullName(OWNER + "/" + REPO);
        rawRepo.setDefaultBranch("main");
        rawRepo.setPrivate(false);

        when(gitHubApiClient.listRepositories(RAW_PAT)).thenReturn(List.of(rawRepo));

        List<GitRepositoryDto> repos = service.listRepositories(testUser);

        assertThat(repos).hasSize(1);
        assertThat(repos.get(0).getFullName()).isEqualTo(OWNER + "/" + REPO);
        assertThat(repos.get(0).getDefaultBranch()).isEqualTo("main");
        assertThat(repos.get(0).isPrivate()).isFalse();

        verify(connectionRepository).findByUserId(USER_ID);
        verify(patEncryptor).decrypt(ENCRYPTED_PAT, IV);
        verify(gitHubApiClient).listRepositories(RAW_PAT);
    }

    @Test
    @DisplayName("listRepositories() should throw when not connected")
    void listRepositories_NotConnected_ThrowsException() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listRepositories(testUser))
                .isInstanceOf(GitHubNotConnectedException.class)
                .hasMessageContaining("Connect GitHub first");

        verify(connectionRepository).findByUserId(USER_ID);
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("listBranches() should return mapped branch list")
    void listBranches_Connected_ReturnsList() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testConnection));
        when(patEncryptor.decrypt(ENCRYPTED_PAT, IV)).thenReturn(RAW_PAT);

        RawGitHubBranch rawBranch = new RawGitHubBranch();
        rawBranch.setName("main");

        when(gitHubApiClient.listBranches(RAW_PAT, OWNER, REPO)).thenReturn(List.of(rawBranch));

        List<BranchDto> branches = service.listBranches(testUser, OWNER, REPO);

        assertThat(branches).hasSize(1);
        assertThat(branches.get(0).getName()).isEqualTo("main");

        verify(connectionRepository).findByUserId(USER_ID);
        verify(patEncryptor).decrypt(ENCRYPTED_PAT, IV);
        verify(gitHubApiClient).listBranches(RAW_PAT, OWNER, REPO);
    }

    @Test
    @DisplayName("listBranches() should throw when not connected")
    void listBranches_NotConnected_ThrowsException() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listBranches(testUser, OWNER, REPO))
                .isInstanceOf(GitHubNotConnectedException.class)
                .hasMessageContaining("Connect GitHub first");

        verify(connectionRepository).findByUserId(USER_ID);
        verifyNoInteractions(gitHubApiClient);
    }
}
