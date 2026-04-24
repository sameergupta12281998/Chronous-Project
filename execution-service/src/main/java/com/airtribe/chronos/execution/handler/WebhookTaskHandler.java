package com.airtribe.chronos.execution.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Test-friendly handler. Honors {"shouldFail": true} or {"shouldFail": "always"}
 * in payload to deterministically throw, enabling reliable retry/terminal tests.
 */
@Component
public class WebhookTaskHandler implements TaskHandler {
    private static final Logger log = LoggerFactory.getLogger(WebhookTaskHandler.class);
    private final ObjectMapper mapper;
    public WebhookTaskHandler(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public String taskType() { return "WEBHOOK"; }
    @Override public void execute(String jobId, String payloadJson) throws TaskExecutionException {
        try {
            JsonNode node = mapper.readTree(payloadJson == null ? "{}" : payloadJson);
            String mode = node.path("shouldFail").asText("");
            if ("always".equalsIgnoreCase(mode) || node.path("shouldFail").asBoolean(false)) {
                throw new TaskExecutionException("Simulated webhook failure for job " + jobId);
            }
            log.info("WEBHOOK handler executed jobId={}", jobId);
        } catch (TaskExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskExecutionException("Bad webhook payload", e);
        }
    }
}
