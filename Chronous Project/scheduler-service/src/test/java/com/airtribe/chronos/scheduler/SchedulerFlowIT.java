package com.airtribe.chronos.scheduler;

import com.airtribe.chronos.commons.event.EventEnvelope;
import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.scheduler.domain.ScheduleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        Topics.JOBS_CREATED, Topics.JOBS_CANCELLED, Topics.JOBS_RESCHEDULED, Topics.JOBS_DUE
})
@ActiveProfiles("test")
class SchedulerFlowIT {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper json;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired EmbeddedKafkaBroker broker;

    @Test
    void jobCreatedThenScannerEmitsJobDue() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Map<String, Object> payload = new HashMap<>();
        payload.put("jobId", jobId.toString());
        payload.put("ownerId", ownerId.toString());
        payload.put("taskType", "EMAIL");
        payload.put("payload", "{\"to\":\"x@y.com\"}");
        payload.put("scheduleType", "ONE_TIME");
        payload.put("recurrenceFrequency", null);
        payload.put("scheduledAt", Instant.now().minusSeconds(1).toString()); // already due
        payload.put("maxAttempts", 3);

        EventEnvelope<Object> env = EventEnvelope.create("JobCreated", 1, jobId.toString(), "corr-1", payload);
        kafka.send(Topics.JOBS_CREATED, jobId.toString(), json.writeValueAsString(env)).get();

        // Verify the schedule got persisted
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(scheduleRepository.findByJobId(jobId)).isPresent());

        // Verify a JobDue event surfaces on the topic
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("scheduler-test-listener", "true", broker);
        consumerProps.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps,
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer())) {
            consumer.subscribe(java.util.List.of(Topics.JOBS_DUE));
            ConsumerRecord<String, String> rec = KafkaTestUtils.getSingleRecord(consumer, Topics.JOBS_DUE,
                    Duration.ofSeconds(10));
            assertThat(rec).isNotNull();
            assertThat(rec.value()).contains("\"eventType\":\"JobDue\"");
            assertThat(rec.value()).contains(jobId.toString());
        }
    }
}
