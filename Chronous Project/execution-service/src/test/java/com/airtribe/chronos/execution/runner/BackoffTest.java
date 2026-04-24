package com.airtribe.chronos.execution.runner;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffTest {
    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertThat(ExecutionRunner.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(ExecutionRunner.backoff(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(ExecutionRunner.backoff(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(ExecutionRunner.backoff(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(ExecutionRunner.backoff(20)).isEqualTo(Duration.ofSeconds(60));
    }
}
