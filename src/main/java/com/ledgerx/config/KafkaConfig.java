package com.ledgerx.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kafka + scheduling wiring (§5 config/). Scheduling backs the `@Scheduled`
 * outbox relay; `@EnableKafka` backs the consumer listeners. Both a `Clock`
 * bean (deterministic time for the relay, §7.4) and the declared topic
 * (compose disables auto topic creation) must exist at startup.
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

    /** Topic contract §8: 3 partitions, key = transferId, cleanup = delete. */
    @Bean
    org.apache.kafka.clients.admin.NewTopic transferPostedTopic() {
        return org.springframework.kafka.config.TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
