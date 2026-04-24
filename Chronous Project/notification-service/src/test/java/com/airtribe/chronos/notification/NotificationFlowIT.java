package com.airtribe.chronos.notification;

import com.airtribe.chronos.commons.event.EventEnvelope;
import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.commons.security.JwtTokenService;
import com.airtribe.chronos.notification.domain.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {
        Topics.JOBS_CREATED, Topics.EXECUTIONS_SUCCEEDED,
        Topics.EXECUTIONS_FAILED, Topics.EXECUTIONS_TERMINAL_FAILURE,
        Topics.NOTIFICATIONS_DISPATCHED
})
@ActiveProfiles("test")
class NotificationFlowIT {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper json;
    @Autowired NotificationRepository repo;
    @Autowired JwtTokenService jwt;
    @Autowired MockMvc mvc;

    @Test
    void executionSucceededProducesNotification() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Map<String, Object> p = new HashMap<>();
        p.put("jobId", jobId.toString());
        p.put("ownerId", owner.toString());
        p.put("status", "SUCCEEDED");
        EventEnvelope<Object> env = EventEnvelope.create("ExecutionSucceeded", 1, jobId.toString(), "corr-1", p);
        kafka.send(Topics.EXECUTIONS_SUCCEEDED, jobId.toString(), json.writeValueAsString(env)).get();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repo.findAll())
                        .anyMatch(n -> n.getJobId().equals(jobId) && n.getDispatchedAt() != null));

        String token = "Bearer " + jwt.issueToken(owner, "alice");
        mvc.perform(get("/notifications").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("ExecutionSucceeded"));
    }

    @Test
    void unauthenticatedListIs401() throws Exception {
        mvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void getByIdReturnsOwnedNotification() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Map<String, Object> p = new HashMap<>();
        p.put("jobId", jobId.toString());
        p.put("ownerId", owner.toString());
        p.put("status", "SUCCEEDED");
        EventEnvelope<Object> env = EventEnvelope.create("ExecutionSucceeded", 1, jobId.toString(), "corr-3", p);
        kafka.send(Topics.EXECUTIONS_SUCCEEDED, jobId.toString(), json.writeValueAsString(env)).get();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repo.findAll()).anyMatch(n -> n.getJobId().equals(jobId)));

        UUID notificationId = repo.findAll().stream()
                .filter(n -> n.getJobId().equals(jobId)).findFirst().orElseThrow().getId();

        String token = "Bearer " + jwt.issueToken(owner, "alice");
        mvc.perform(get("/notifications/" + notificationId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));

        // Cross-owner is 403
        String otherToken = "Bearer " + jwt.issueToken(UUID.randomUUID(), "bob");
        mvc.perform(get("/notifications/" + notificationId).header("Authorization", otherToken))
                .andExpect(status().isForbidden());

        // Unknown id is 404
        mvc.perform(get("/notifications/" + UUID.randomUUID()).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void terminalFailureMessagesIncludeError() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Map<String, Object> p = new HashMap<>();
        p.put("jobId", jobId.toString());
        p.put("ownerId", owner.toString());
        p.put("error", "boom");
        EventEnvelope<Object> env = EventEnvelope.create("ExecutionTerminalFailure", 1, jobId.toString(), "corr-2", p);
        kafka.send(Topics.EXECUTIONS_TERMINAL_FAILURE, jobId.toString(), json.writeValueAsString(env)).get();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repo.findAll())
                        .anyMatch(n -> n.getJobId().equals(jobId) && n.getMessage().contains("boom")));
    }
}
