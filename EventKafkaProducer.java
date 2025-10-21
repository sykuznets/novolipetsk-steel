package com.example.project.kafka.service.impl;

import com.example.project.dto.GenericEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventKafkaProducer {

    private final KafkaTemplate<String, GenericEvent> kafkaTemplate;

    @Value("${app.kafka.producer.topic.name}")
    private String topic;

    public void sendEvent(GenericEvent event) {
        kafkaTemplate.send(topic, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Message successfully sent to topic: '{}'", topic);
                    } else {
                        log.error("Failed to send message to topic: '{}'", topic, ex);
                    }
                });
    }
  
}
