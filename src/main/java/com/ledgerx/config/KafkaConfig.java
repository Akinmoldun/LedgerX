package com.ledgerx.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kafka and scheduling configuration. Scheduling drives the background outbox relay,
 * while Kafka listeners consume topic events. The Clock bean supports testable,
 * deterministic time calculations, and the NewTopic bean ensures the topic exists at startup.
 */
@Configuration
@EnableScheduling
@EnableKafka
public class KafkaConfig {

    private final String topic;

    public KafkaConfig(@Value("${ledgerx.kafka.topic:ledgerx.transfer.posted.v1}") String topic) {
        this.topic = topic;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Configures transfer posted topic with 3 partitions and replication factor 1. */
    @Bean
    org.apache.kafka.clients.admin.NewTopic transferPostedTopic() {
        return org.springframework.kafka.config.TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
