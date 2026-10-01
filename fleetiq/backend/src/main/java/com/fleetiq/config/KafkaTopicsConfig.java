package com.fleetiq.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic vehicleSearchesTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().vehicleSearches()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic bookingEventsTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().bookingEvents()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic notificationsTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().notifications()).partitions(1).replicas(1).build();
    }
}
