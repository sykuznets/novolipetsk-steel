package application.listeners;

import domain.operations.GenericOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@KafkaListener(
    topics = {"${app.kafka.consumer.topics.event.name}"},
    groupId = "${app.kafka.consumer.group-id}",
    containerFactory = "kafkaListenerEvent"
)
@Slf4j
public class GenericEventListener {

  private final GenericOperation operation;
  private final EventListenerMapper eventMapper;

  @KafkaHandler
  public void handleEvent(GenericEvent event) {
    log.info("Received GenericEvent: '{}'", event);

    var operationType = eventMapper.toDomain(event.getEventData().getOperationType());
    var entityId = event.getEventPayload().getId();

    operation.execute(entityId, operationType);
  }
}
