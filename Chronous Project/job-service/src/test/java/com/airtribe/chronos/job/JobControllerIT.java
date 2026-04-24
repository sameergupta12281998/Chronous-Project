package com.airtribe.chronos.job;

import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.commons.security.JwtTokenService;
import com.airtribe.chronos.job.consumer.ProcessedEventRepository;
import com.airtribe.chronos.job.domain.JobRepository;
import com.airtribe.chronos.job.domain.JobStatus;
import com.airtribe.chronos.job.outbox.OutboxRepository;
import com.airtribe.chronos.job.web.CreateJobRequest;
import com.airtribe.chronos.job.web.RescheduleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {
        Topics.JOBS_CREATED, Topics.JOBS_CANCELLED, Topics.JOBS_RESCHEDULED,
        Topics.EXECUTIONS_STARTED, Topics.EXECUTIONS_SUCCEEDED,
        Topics.EXECUTIONS_FAILED, Topics.EXECUTIONS_TERMINAL_FAILURE
})
@ActiveProfiles("test")
class JobControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenService jwt;
    @Autowired JobRepository jobRepo;
    @Autowired OutboxRepository outboxRepo;

    private String tokenFor(UUID userId) {
        return "Bearer " + jwt.issueToken(userId, "user-" + userId);
    }

    @Test
    void createListGetJob() throws Exception {
        UUID owner = UUID.randomUUID();
        CreateJobRequest req = new CreateJobRequest(
                "send-email", "welcome email", "EMAIL", "{\"to\":\"a@b.com\"}",
                com.airtribe.chronos.job.domain.ScheduleType.ONE_TIME, null,
                Instant.now().plusSeconds(3600), 3);

        MvcResult created = mvc.perform(post("/jobs")
                        .header("Authorization", tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.taskType").value("EMAIL"))
                .andReturn();

        Map<?, ?> body = json.readValue(created.getResponse().getContentAsString(), Map.class);
        String jobId = (String) body.get("id");

        mvc.perform(get("/jobs").header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/jobs/" + jobId).header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId));

        // Outbox should contain a JobCreated event
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(outboxRepo.findAll())
                        .anyMatch(o -> "JobCreated".equals(o.getEventType()))
        );
    }

    @Test
    void cancellingOthersJobIsForbidden() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        CreateJobRequest req = new CreateJobRequest(
                "ping", null, "WEBHOOK", "{}",
                com.airtribe.chronos.job.domain.ScheduleType.ONE_TIME, null,
                Instant.now().plusSeconds(60), 1);

        MvcResult res = mvc.perform(post("/jobs")
                        .header("Authorization", tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        String id = (String) json.readValue(res.getResponse().getContentAsString(), Map.class).get("id");

        mvc.perform(delete("/jobs/" + id).header("Authorization", tokenFor(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIs401() throws Exception {
        mvc.perform(get("/jobs")).andExpect(status().isUnauthorized());
    }

    @Test
    void rescheduleSucceedsAndProducesEvent() throws Exception {
        UUID owner = UUID.randomUUID();
        CreateJobRequest req = new CreateJobRequest(
                "report", null, "REPORT", "{}",
                com.airtribe.chronos.job.domain.ScheduleType.ONE_TIME, null,
                Instant.now().plusSeconds(120), 1);
        MvcResult res = mvc.perform(post("/jobs")
                        .header("Authorization", tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        String id = (String) json.readValue(res.getResponse().getContentAsString(), Map.class).get("id");

        RescheduleRequest reschedule = new RescheduleRequest(Instant.now().plusSeconds(7200));
        mvc.perform(patch("/jobs/" + id + "/reschedule")
                        .header("Authorization", tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reschedule)))
                .andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(outboxRepo.findAll())
                        .anyMatch(o -> "JobRescheduled".equals(o.getEventType()))
        );
    }

    @Test
    void postCancelAliasWorks() throws Exception {
        UUID owner = UUID.randomUUID();
        CreateJobRequest req = new CreateJobRequest(
                "alias-cancel", null, "EMAIL", "{}",
                com.airtribe.chronos.job.domain.ScheduleType.ONE_TIME, null,
                Instant.now().plusSeconds(60), 1);
        MvcResult res = mvc.perform(post("/jobs")
                        .header("Authorization", tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        String id = (String) json.readValue(res.getResponse().getContentAsString(), Map.class).get("id");

        mvc.perform(post("/jobs/" + id + "/cancel")
                        .header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void idempotencyKeyReturnsSameJobOnRepeat() throws Exception {
        UUID owner = UUID.randomUUID();
        String key = "order-12345";
        CreateJobRequest req = new CreateJobRequest(
                "idem-job", null, "EMAIL", "{}",
                com.airtribe.chronos.job.domain.ScheduleType.ONE_TIME, null,
                Instant.now().plusSeconds(120), 2);

        MvcResult first = mvc.perform(post("/jobs")
                        .header("Authorization", tokenFor(owner))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        String firstId = (String) json.readValue(first.getResponse().getContentAsString(), Map.class).get("id");

        MvcResult second = mvc.perform(post("/jobs")
                        .header("Authorization", tokenFor(owner))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())   // 200 not 201 on idempotent replay
                .andReturn();
        String secondId = (String) json.readValue(second.getResponse().getContentAsString(), Map.class).get("id");

        assertThat(secondId).isEqualTo(firstId);
        // Only one job was actually created
        assertThat(jobRepo.findAll().stream().filter(j -> j.getOwnerId().equals(owner)).count()).isEqualTo(1L);
    }

    @Test
    void scheduledAtInPastIs400() throws Exception {
        UUID owner = UUID.randomUUID();
        CreateJobRequest req = new CreateJobRequest(
                "past-job", null, "EMAIL", "{}",
                com.airtribe.chronos.job.domain.ScheduleType.ONE_TIME, null,
                Instant.now().minusSeconds(120), 1);
        mvc.perform(post("/jobs")
                        .header("Authorization", tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void recurringWithoutFrequencyIs400() throws Exception {
        UUID owner = UUID.randomUUID();
        CreateJobRequest req = new CreateJobRequest(
                "recurring-bad", null, "EMAIL", "{}",
                com.airtribe.chronos.job.domain.ScheduleType.RECURRING, null,
                Instant.now().plusSeconds(60), 1);
        mvc.perform(post("/jobs")
                        .header("Authorization", tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
