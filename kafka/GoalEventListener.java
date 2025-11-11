package com.example.listeners;

import static com.example.util.DecodeUtil.decrypt;

import com.example.dto.AssignmentPut;
import com.example.dto.GoalPut;
import com.example.dto.PutGoalRequest;
import com.example.exceptions.ParentGoalNotFoundException;
import com.example.model.*;
import com.example.operations.FetchGoalsOperation;
import com.example.operations.SendGoalUpdateOperation;
import com.example.ports.DeferredGoalPort;
import com.example.ports.UserPort;
import com.example.config.AppConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalEventListener {

  private static final int MAX_ATTEMPTS = 3;

  private final SendGoalUpdateOperation sendGoalUpdateOperation;
  private final FetchGoalsOperation fetchGoalsOperation;
  private final UserPort userPort;
  private final DeferredGoalPort deferredGoalPort;

  private final AppConfig.DecodeProperties decodeProps;
  private final ObjectMapper objectMapper;

  @KafkaListener(
      topics = "${app.kafka.consumer.topics.goal.name}",
      groupId = "${app.kafka.consumer.group-id}",
      containerFactory = "kafkaListener"
  )
  public void onMessage(ConsumerRecord<String, GoalEvent> message) {
    if (message == null || message.value() == null) {
      log.warn("Received null Kafka record — skipping");
      return;
    }

    GoalEvent event = message.value();
    GoalRecord data = event.getData();

    Integer goalId = data.getGoalId();
    Integer parentGoalId = data.getParentGoalId();
    String userId = decrypt(data.getUserId(), decodeProps.getSecret(), decodeProps.getVector());
    String title = decrypt(data.getTitle(), decodeProps.getSecret(), decodeProps.getVector());

    log.info("Processing goal event goalId = '{}', parentGoalId = '{}'", goalId, parentGoalId);

    var goalsResponse = fetchGoalsOperation.execute(goalId, parentGoalId);

    boolean isPostponed = handleMissingGoals(userId, title, goalsResponse, data, event);
    if (isPostponed) return;

    if (goalsResponse.goal() != null && goalsResponse.parentGoal() == null) {
      goalsResponse = retryFetchingGoals(goalId, parentGoalId);
    }

    Long profileId = userPort.findProfileIdByEmployeeNumber(userId).orElse(null);

    sendDeferredGoalUpdates(goalId, profileId, goalsResponse.parentGoal());
    sendGoalUpdateFromEvent(title, data, event, goalsResponse, profileId);

    log.info("Successfully processed goal event [goalId={}]", goalId);
  }

  private boolean handleMissingGoals(
      String userId,
      String title,
      Goals goals,
      GoalRecord data,
      GoalEvent event
  ) {
    if (goals.goal() != null || goals.parentGoal() != null) {
      return false;
    }

    var deferredGoal = new DeferredGoal(
        null,
        (long) data.getGoalId(),
        (long) data.getParentGoalId(),
        userId,
        serializeToJson(title, data.getTemplateYear(), event.isDeleteOperation()),
        OffsetDateTime.parse(event.getTimestamp()).toLocalDateTime()
    );

    deferredGoalPort.saveIfAbsentOrNewer(deferredGoal);
    log.debug("Goal postponed — missing goal entities, saved to DeferredGoal");

    return true;
  }

  private String serializeToJson(String title, int templateYear, boolean isDelete) {
    try {
      Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put("title", title);
      jsonMap.put("templateYear", templateYear);
      jsonMap.put("isDelete", isDelete);
      
      return objectMapper.writeValueAsString(jsonMap);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize goal data to JSON", e);
    }
  }

  private Goals retryFetchingGoals(Integer goalId, Integer parentGoalId) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      var response = fetchGoalsOperation.execute(goalId, parentGoalId);
      if (response.parentGoal() != null) {
        log.debug("Successfully fetched missing parent goal on attempt {}", attempt);
        return response;
      }
    }

    throw new ParentGoalNotFoundException(String.format(
        "Parent goal not found for goalId=%s, parentGoalId=%s after %d attempts",
        goalId, parentGoalId, MAX_ATTEMPTS
    ));
  }

  private void sendDeferredGoalUpdates(Integer goalId, Long profileId, Goal parentGoal) {
    var pendingGoals = deferredGoalPort.findByParentGoalExternalId(Long.valueOf(goalId));

    pendingGoals.forEach(pending -> {
      PostponedGoalData data = parseDeferredGoalData(pending);
      if (data == null) return;

      Long timestamp = pending.timestamp().toInstant(ZoneOffset.UTC).toEpochMilli();
      Integer sourceSystemId = pending.goalExternalId().intValue();
      var triple = Triple.of(data.title(), data.templateYear(), data.isDelete());
      var request = buildPutGoalRequest(profileId, parentGoal, sourceSystemId, triple, timestamp);

      sendGoalUpdateWithRetry(pending.id(), request);
    });
  }

  private PutGoalRequest buildPutGoalRequest(
      Long profileId,
      Goal parentGoal,
      Integer sourceSystemId,
      Triple<String, Integer, Boolean> triple,
      Long timestamp
  ) {
    var assignment = new AssignmentPut();
    assignment.setCascadedGoalId(Optional.ofNullable(parentGoal).map(Goal::id).orElse(null));
    assignment.setSubordinateProfileId(profileId);

    var goalPut = new GoalPut();
    goalPut.setSourceSystemId(sourceSystemId);
    goalPut.setTitle(triple.getLeft());
    goalPut.setYear(triple.getMiddle());
    goalPut.isDelete(triple.getRight());
    goalPut.setOwnerProfileId(profileId);
    goalPut.setTimestamp(timestamp);

    var request = new PutGoalRequest();
    request.setGoal(goalPut);
    request.setAssignment(assignment);
    return request;
  }

  private PostponedGoalData parseDeferredGoalData(DeferredGoal deferredGoal) {
    try {
      return objectMapper.readValue(deferredGoal.data(), new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.error("Failed to parse JSON for DeferredGoal ID {}: {}", deferredGoal.id(), e.getMessage());
      return null;
    }
  }

  private void sendGoalUpdateWithRetry(Long deferredGoalId, PutGoalRequest request) {
    log.debug("Sending goal update...");

    Exception lastException = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        sendGoalUpdateOperation.execute(request);
        deferredGoalPort.deleteById(deferredGoalId);
        return;
      } catch (Exception e) {
        lastException = e;
        log.warn("Attempt {}/{} to send goal update failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
      }
    }

    log.error("Failed to send goal update after {} attempts for DeferredGoal ID = {}", MAX_ATTEMPTS, deferredGoalId, lastException);
  }

  private void sendGoalUpdateFromEvent(
      String title,
      GoalRecord data,
      GoalEvent event,
      Goals goals,
      Long profileId
  ) {
    var triple = Triple.of(title, data.getTemplateYear(), event.isDeleteOperation());
    Long timestamp = OffsetDateTime.parse(event.getTimestamp()).toInstant().toEpochMilli();
    Goal parentGoal = goals.parentGoal();

    var request = buildPutGoalRequest(profileId, parentGoal, data.getGoalId(), triple, timestamp);
    sendGoalUpdateOperation.execute(request);
  }
  
}
