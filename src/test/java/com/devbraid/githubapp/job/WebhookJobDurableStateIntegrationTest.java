package com.devbraid.githubapp.job;

import com.devbraid.githubapp.entity.GitHubAppInstallation;
import com.devbraid.githubapp.entity.GitHubIdentity;
import com.devbraid.githubapp.entity.GitHubWebhook;
import com.devbraid.githubapp.entity.JobStatus;
import com.devbraid.githubapp.entity.WebhookJob;
import com.devbraid.githubapp.repository.GitHubAppInstallationRepository;
import com.devbraid.githubapp.repository.GitHubIdentityRepository;
import com.devbraid.githubapp.repository.GitHubWebhookRepository;
import com.devbraid.githubapp.repository.WebhookJobRepository;
import com.devbraid.githubapp.service.GitHubWebhookService;
import com.devbraid.user.entity.User;
import com.devbraid.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PG-backed integration tests for the durable webhook-job state machine
 * ({@link WebhookJobWorker} / {@link WebhookJobProcessor}).
 * <p>
 * Why Testcontainers instead of H2: the claim query uses {@code FOR UPDATE SKIP LOCKED}
 * and the migrations use {@code uuidv7()} (V1 onwards), neither of which H2 supports. These tests
 * require a running Docker daemon (Testcontainers starts postgres:18-alpine + redis:7-alpine) —
 * the same dependency the existing {@code @SpringBootTest} context tests already have on the dev stack.
 * are the regression lock for the P0 bug the live Docker smoke caught: a handler failure
 * propagating through the nested {@code @Transactional} {@code createThreadForInstallation}
 * used to mark the job-state transaction rollback-only, silently discarding the attempt/
 * backoff bookkeeping (job re-claimed forever at attempts=0). The fix was
 * {@code GitHubWebhookService.dispatch} running in {@code REQUIRES_NEW} — the failure
 * tests below fail if that annotation is removed.
 * <p>
 * The real scheduler is neutralized via a 1-hour poll interval so it never races the
 * assertions; every test drives {@code processOne()} directly.
 */
@Testcontainers
@SpringBootTest(properties = {
        // Keep the real @Scheduled worker out of the test — first run is 1h away.
        "app.webhook-jobs.poll-interval-ms=3600000",
        // Pin the defaults the tests hard-code (dead-letter seeds attempts=4 for max=5;
        // doubling asserts the 2^n × base backoff math) so they're self-contained.
        "app.webhook-jobs.max-attempts=5",
        "app.webhook-jobs.backoff-base-ms=2000",
        // app.jwt.secret has no default in application.yml — required to boot the context.
        "app.jwt.secret=integration-test-secret",
        // Pin GitHub App credentials EMPTY: the failure-path tests depend on
        // createAppJwt() throwing "GitHub App credentials not configured". If the
        // environment exports GITHUB_APP_ID/GITHUB_APP_PRIVATE_KEY, createAppJwt()
        // would succeed and the tests would hit api.github.com over the network.
        "github.app.app-id=",
        "github.app.private-key=",
        // Flyway owns the schema (V1–V20 run on the container); Hibernate must not alter it.
        "spring.jpa.hibernate.ddl-auto=none"
})
class WebhookJobDurableStateIntegrationTest {

    private static final long INSTALLATION_ID = 12345L;
    private static final long GITHUB_USER_ID = 999L;

    // postgres:18-alpine matches the compose stack — required for uuidv7() defaults.
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("devbraid")
            .withUsername("devbraid_user")
            .withPassword("devbraid_pass");

    // GitHubAppTokenService reads Redis before exchanging tokens; a running Redis makes
    // the failure path deterministic ("GitHub App credentials not configured").
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private WebhookJobProcessor processor;
    @Autowired
    private GitHubWebhookService webhookService;
    @Autowired
    private WebhookJobRepository jobRepository;
    @Autowired
    private GitHubWebhookRepository webhookRepository;
    @Autowired
    private GitHubAppInstallationRepository installationRepository;
    @Autowired
    private GitHubIdentityRepository identityRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private GitHubWebhook webhook;
    private WebhookJob job;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE webhook_jobs, github_webhooks, "
                + "github_app_installations, github_identities, users CASCADE");
    }

    // ── Seeding helpers ─────────────────────────────────────────────

    private void seedUser() {
        user = userRepository.save(User.builder()
                .email("it-" + UUID.randomUUID() + "@devbraid.test")
                .fullName("Integration Test User")
                .passwordHash("not-a-real-hash")
                .build());
    }

    private void seedIdentity() {
        identityRepository.save(GitHubIdentity.builder()
                .user(user)
                .githubUserId(GITHUB_USER_ID)
                .githubLogin("integration-user")
                .build());
    }

    private void seedInstallation() {
        installationRepository.save(GitHubAppInstallation.builder()
                .user(user)
                .installationId(INSTALLATION_ID)
                .accountLogin("integration-user")
                .build());
    }

    /** Enqueue a webhook + PENDING job row directly (as receiveWebhook would). */
    private void enqueue(String eventType, String action, JsonNode payload) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(payload);
        webhook = webhookRepository.save(GitHubWebhook.builder()
                .installationId(INSTALLATION_ID)
                .eventType(eventType)
                .action(action)
                .deliveryId(UUID.randomUUID().toString())
                .payload(payloadJson)
                .processed(false)
                .build());
        job = jobRepository.save(WebhookJob.builder()
                .webhookId(webhook.getId())
                .eventType(eventType)
                .payload(payloadJson)
                .status(JobStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(OffsetDateTime.now().minusSeconds(1))
                .build());
    }

    private JsonNode pullRequestPayload() throws Exception {
        return objectMapper.readTree("""
                {
                  "action": "opened",
                  "repository": {"full_name": "owner/integration-repo", "name": "integration-repo", "owner": {"login": "owner"}},
                  "pull_request": {
                    "number": 1,
                    "title": "Integration test PR",
                    "body": null,
                    "head": {"ref": "feature/integration", "sha": "deadbeef"},
                    "base": {"ref": "main"}
                  }
                }
                """);
    }

    private JsonNode installationCreatedPayload() throws Exception {
        return objectMapper.readTree("""
                {
                  "action": "created",
                  "installation": {"id": 12345, "account": {"id": 999, "login": "integration-user", "type": "User"}, "repository_selection": "all"},
                  "sender": {"id": 999}
                }
                """);
    }

    private WebhookJob reloadJob() {
        return jobRepository.findById(job.getId()).orElseThrow();
    }

    // ── Tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("successful dispatch marks the job SUCCEEDED and the webhook processed, persisting the installation")
    void processOne_success_marksSucceededAndPersistsInstallation() throws Exception {
        seedUser();
        seedIdentity();
        enqueue("installation", "created", installationCreatedPayload());

        boolean processed = processor.processOne();

        assertThat(processed).isTrue();
        WebhookJob reloaded = reloadJob();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(reloaded.getAttempts()).isZero();

        GitHubWebhook reloadedWebhook = webhookRepository.findById(webhook.getId()).orElseThrow();
        assertThat(reloadedWebhook.getProcessed()).isTrue();
        assertThat(reloadedWebhook.getProcessedAt()).isNotNull();

        GitHubAppInstallation persisted =
                installationRepository.findByInstallationId(INSTALLATION_ID).orElseThrow();
        assertThat(persisted.getUser().getId()).isEqualTo(user.getId());
        assertThat(persisted.getActive()).isTrue();
        assertThat(persisted.getSuspended()).isFalse();
        assertThat(persisted.getAccountLogin()).isEqualTo("integration-user");
    }

    @Test
    @DisplayName("REQUIRES_NEW regression: a handler failure persists the attempt/backoff state in a fresh transaction")
    void processOne_failure_persistsAttemptStateAcrossTransactions() throws Exception {
        seedUser();
        seedInstallation();
        enqueue("pull_request", "opened", pullRequestPayload());

        boolean processed = processor.processOne();

        // Without REQUIRES_NEW on dispatch, the shared tx would be marked rollback-only and
        // this save would be discarded — the reload would show attempts=0 and the test fails.
        assertThat(processed).isTrue();
        WebhookJob reloaded = reloadJob();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getLastError()).contains("GitHub App credentials not configured");
        assertThat(reloaded.getNextAttemptAt()).isAfter(OffsetDateTime.now());

        // The dispatch (and its failure) ran in its own transaction — the webhook must not
        // be marked processed by a failed dispatch.
        GitHubWebhook reloadedWebhook = webhookRepository.findById(webhook.getId()).orElseThrow();
        assertThat(reloadedWebhook.getProcessed()).isFalse();
    }

    @Test
    @DisplayName("subsequent failures double the backoff window")
    void processOne_doublesBackoffOnSubsequentFailure() throws Exception {
        seedUser();
        seedInstallation();
        enqueue("pull_request", "opened", pullRequestPayload());
        job.setAttempts(1);
        jobRepository.save(job);

        processor.processOne();

        WebhookJob reloaded = reloadJob();
        assertThat(reloaded.getAttempts()).isEqualTo(2);
        // attempt 2 → 2000ms * 2^(2-1) = 4000ms
        assertThat(reloaded.getNextAttemptAt()).isAfter(OffsetDateTime.now().plusSeconds(2));
        assertThat(reloaded.getNextAttemptAt()).isBefore(OffsetDateTime.now().plusSeconds(10));
    }

    @Test
    @DisplayName("dead-letters the job as FAILED after max attempts and stops claiming it")
    void processOne_deadLettersAfterMaxAttempts() throws Exception {
        seedUser();
        seedInstallation();
        enqueue("pull_request", "opened", pullRequestPayload());
        job.setAttempts(4); // max-attempts = 5
        jobRepository.save(job);

        boolean processed = processor.processOne();

        assertThat(processed).isTrue();
        WebhookJob reloaded = reloadJob();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(reloaded.getAttempts()).isEqualTo(5);
        assertThat(reloaded.getLastError()).contains("GitHub App credentials not configured");

        // FAILED rows are not claimable — subsequent polls return false.
        assertThat(processor.processOne()).isFalse();
        assertThat(reloadJob().getAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("jobs whose next_attempt_at is in the future are not claimed")
    void processOne_doesNotClaimJobNotYetDue() throws Exception {
        seedUser();
        seedInstallation();
        enqueue("pull_request", "opened", pullRequestPayload());
        job.setNextAttemptAt(OffsetDateTime.now().plusHours(1));
        jobRepository.save(job);

        assertThat(processor.processOne()).isFalse();

        WebhookJob reloaded = reloadJob();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(reloaded.getAttempts()).isZero();
    }

    @Test
    @DisplayName("concurrent claims under SKIP LOCKED process the job exactly once")
    void processOne_concurrentClaims_claimExactlyOnce() throws Exception {
        seedUser();
        seedInstallation();
        enqueue("pull_request", "opened", pullRequestPayload());

        int workers = 2;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CyclicBarrier barrier = new CyclicBarrier(workers);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    return processor.processOne();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        // Exactly one worker claims and processes the single due job; the other claims nothing.
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        WebhookJob reloaded = reloadJob();
        assertThat(reloaded.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("receiveWebhook → worker end-to-end: enqueue, claim, succeed on real PG (JSONB round-trip)")
    void receiveWebhookThroughWorker_endToEnd_succeeds() throws Exception {
        seedUser();
        seedIdentity();

        var response = webhookService.receiveWebhook(
                "installation",
                UUID.randomUUID().toString(),
                "created",
                installationCreatedPayload(),
                INSTALLATION_ID);
        assertThat(response.getReplayed()).isFalse();

        // Job enqueued by receiveWebhook with the webhook id, due immediately.
        job = jobRepository.findAll().stream()
                .filter(j -> j.getWebhookId().equals(response.getId()))
                .findFirst().orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();

        assertThat(processor.processOne()).isTrue();
        assertThat(reloadJob().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(installationRepository.findByInstallationId(INSTALLATION_ID)).isPresent();
    }
}
