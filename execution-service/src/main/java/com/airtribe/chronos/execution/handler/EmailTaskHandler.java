package com.airtribe.chronos.execution.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailTaskHandler implements TaskHandler {
    private static final Logger log = LoggerFactory.getLogger(EmailTaskHandler.class);
    private final ObjectMapper mapper;
    public EmailTaskHandler(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public String taskType() { return "EMAIL"; }
    @Override public void execute(String jobId, String payloadJson) {
        log.info("EMAIL handler executing for job {} with payload {}", jobId, payloadJson);
    }
}
