package com.airtribe.chronos.execution;

import com.airtribe.chronos.commons.event.EventEnvelope;
import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.execution.domain.ExecutionEntity;
import com.airtribe.chronos.execution.domain.ExecutionRepository;
import com.airtribe.chronos.execution.domain.ExecutionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        Topics.JOBS_DUE, Topics.EXECUTIONS_STARTED, Topics.EXECUTIONS_SUCCEEDED,
        Topics.EXECUTIONS_FAILED, Topics.EXECUTIONS_TERMINAL_FAILURE
})
@ActiveProfiles("test")
class ExecutionFlowIT {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper json;
    @Autowired ExecutionRepository executionRepository;

    private void sendJobDue(UUID jobId, String taskType, String payload, int maxAttempts) throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("jobId", jobId.toString());
        p.put("ownerId", UUID.randomUUID().toString());
        p.put("taskType", taskType);
        p.put("payload", payload);
        p.put("scheduledAt", java.time.Instant.now().toString());
        p.put("maxAttempts", maxAttempts);
        EventEnvelope<Object> env = EventEnvelope.create("JobDue", 1, jobId.toString(), "corr-" + jobId, p);
        kafka.send(Topics.JOBS_DUE, jobId.toString(), json.writeValueAsString(env)).get();
    }

    @Test
    void successfulEmailJobReachesSucceeded() throws Exception {
        UUID jobId = UUID.randomUUID();
        sendJobDue(jobId, "EMAIL", "{}", 3);
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var execs = executionRepository.findByJobIdOrderByAttemptAsc(jobId);
            assertThat(execs).isNotEmpty();
            assertThat(execs.get(0).getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        });
    }

    @Test
    void failingWebhookRetriesAndReachesTerminalFailure() throws Exception {
        UUID jobId = UUID.randomUUID();
        sendJobDue(jobId, "WEBHOOK", "{\"shouldFail\":true}", 2);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var execs = executionRepository.findByJobIdOrderByAttemptAsc(jobId);
            assertThat(execs).hasSizeGreaterThanOrEqualTo(2);
            ExecutionEntity last = execs.get(execs.size() - 1);
            assertThat(last.getStatus()).isEqualTo(ExecutionStatus.TERMINAL_FAILURE);
            assertThat(last.getAttempt()).isEqualTo(2);
        });
    }

    @Test
    void duplicateJobDueIsIdempotent() throws Exception {
        UUID jobId = UUID.randomUUID();
        // Send the same eventId twice
        Map<String, Object> p = new HashMap<>();
        p.put("jobId", jobId.toString());
        p.put("ownerId", UUID.randomUUID().toString());
        p.put("taskType", "EMAIL");
        p.put("payload", "{}");
        p.put("scheduledAt", java.time.Instant.now().toString());
        p.put("maxAttempts", 1);
        EventEnvelope<Object> env = EventEnvelope.create("JobDue", 1, jobId.toString(), "corr-x", p);
        String msg = json.writeValueAsString(env);
        kafka.send(Topics.JOBS_DUE, jobId.toString(), msg).get();
        kafka.send(Topics.JOBS_DUE, jobId.toString(), msg).get();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var execs = executionRepository.findByJobIdOrderByAttemptAsc(jobId);
            assertThat(execs).hasSize(1);
        });
    }
}
