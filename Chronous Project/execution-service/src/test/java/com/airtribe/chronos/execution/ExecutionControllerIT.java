package com.airtribe.chronos.execution;

import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.commons.security.JwtTokenService;
import com.airtribe.chronos.execution.domain.ExecutionEntity;
import com.airtribe.chronos.execution.domain.ExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {
        Topics.JOBS_DUE, Topics.EXECUTIONS_STARTED, Topics.EXECUTIONS_SUCCEEDED,
        Topics.EXECUTIONS_FAILED, Topics.EXECUTIONS_TERMINAL_FAILURE
})
@ActiveProfiles("test")
class ExecutionControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ExecutionRepository repo;
    @Autowired JwtTokenService jwt;

    private String tokenFor(UUID userId) {
        return "Bearer " + jwt.issueToken(userId, "user-" + userId);
    }

    @Test
    void getExecutionByIdReturnsOwnedRow() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ExecutionEntity e = new ExecutionEntity(jobId, owner, "EMAIL", "{}", 1, 3, "corr-it");
        repo.save(e);

        mvc.perform(get("/executions/" + e.getId()).header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(e.getId().toString()))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("STARTED"));
    }

    @Test
    void otherUsersExecutionIs403() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ExecutionEntity e = new ExecutionEntity(jobId, owner, "EMAIL", "{}", 1, 3, null);
        repo.save(e);

        mvc.perform(get("/executions/" + e.getId()).header("Authorization", tokenFor(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownExecutionIs404() throws Exception {
        UUID owner = UUID.randomUUID();
        mvc.perform(get("/executions/" + UUID.randomUUID()).header("Authorization", tokenFor(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mvc.perform(get("/executions/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void listByJobIdReturnsAttempts() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        repo.save(new ExecutionEntity(jobId, owner, "EMAIL", "{}", 1, 3, null));
        repo.save(new ExecutionEntity(jobId, owner, "EMAIL", "{}", 2, 3, null));

        mvc.perform(get("/executions").param("jobId", jobId.toString()).header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void listWithoutJobIdIs400() throws Exception {
        UUID owner = UUID.randomUUID();
        mvc.perform(get("/executions").header("Authorization", tokenFor(owner)))
                .andExpect(status().isBadRequest());
    }
}
