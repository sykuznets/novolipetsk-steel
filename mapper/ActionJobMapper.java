package com.example.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import com.example.dto.GoalDto;
import com.example.dto.GoalEvent;
import com.example.exception.ActionJobMappingException;
import com.example.model.ActionJob;
import com.example.model.Goal;
import com.example.model.enums.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps goal-related events and entities to ActionJob records
 * for asynchronous or scheduled processing.
 */
@Mapper(componentModel = SPRING)
public interface ActionJobMapper {

    @Mapping(target = "data", expression = "java(serializeToJson(event.goalDto()))")
    @Mapping(target = "status", expression = "java(com.example.model.enums.JobStatus.NEW)")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "messageType", target = "messageType")
    ActionJob toEntity(GoalEvent event, String messageType);

    @Mapping(target = "data", expression = "java(serializeToJson(goalMapper.toDto(goal)))")
    @Mapping(target = "status", expression = "java(com.example.model.enums.JobStatus.NEW)")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ActionJob toEntity(
            Goal goal,
            String messageType,
            @Context GoalMapper goalMapper
    );

    default String serializeToJson(GoalDto goalDto) {
        try {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("id", goalDto.getId());
            jsonMap.put("userId", goalDto.getUser().getEmployeeId());
            jsonMap.put("title", goalDto.getTitle());
            jsonMap.put("parentGoalId", goalDto.getParentGoal() != null ? goalDto.getParentGoal().getId() : null);
            jsonMap.put("messageType", "CreateGoal");
            jsonMap.put("lastModified", goalDto.getLastModifiedDateTime().toEpochSecond(ZoneOffset.UTC));
            jsonMap.put("templateYear", goalDto.getTemplate().getYear());

            return new ObjectMapper().writeValueAsString(jsonMap);
        } catch (Exception e) {
            throw new ActionJobMappingException("Failed to serialize GoalDto to JSON", e);
        }
    }
  
}
