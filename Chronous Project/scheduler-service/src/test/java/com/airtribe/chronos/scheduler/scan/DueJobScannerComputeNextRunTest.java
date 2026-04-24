package com.airtribe.chronos.scheduler.scan;

import com.airtribe.chronos.scheduler.domain.ScheduleEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DueJobScannerComputeNextRunTest {

    @Test
    void oneTimeReturnsNull() throws Exception {
        ScheduleEntity s = new ScheduleEntity(UUID.randomUUID(), UUID.randomUUID(),
                "EMAIL", "{}", "ONE_TIME", null, Instant.now(), 3);
        assertThat(DueJobScanner.computeNextRun(s)).isNull();
    }

    @Test
    void hourlyAdvancesByOneHour() {
        Instant base = Instant.parse("2025-01-01T00:00:00Z");
        ScheduleEntity s = new ScheduleEntity(UUID.randomUUID(), UUID.randomUUID(),
                "EMAIL", "{}", "RECURRING", "HOURLY", base, 3);
        assertThat(DueJobScanner.computeNextRun(s)).isEqualTo(base.plus(1, ChronoUnit.HOURS));
    }

    @Test
    void dailyAdvancesByOneDay() {
        Instant base = Instant.parse("2025-01-01T00:00:00Z");
        ScheduleEntity s = new ScheduleEntity(UUID.randomUUID(), UUID.randomUUID(),
                "EMAIL", "{}", "RECURRING", "DAILY", base, 3);
        assertThat(DueJobScanner.computeNextRun(s)).isEqualTo(base.plus(1, ChronoUnit.DAYS));
    }
}
