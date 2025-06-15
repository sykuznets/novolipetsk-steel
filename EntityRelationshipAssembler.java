package com.example.service.helper;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static org.springframework.util.CollectionUtils.isEmpty;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import com.example.dto.*;
import com.example.exceptions.AppException;
import com.example.exceptions.NotFoundException;
import com.example.mapper.EntityMapper;
import com.example.repository.*;
import com.example.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityRelationshipAssembler {

    private final ActionRepository actionRepository;
    private final TaskDisciplineLinkRepository taskDisciplineLinkRepository;
    private final ResponsibilityRepository responsibilityRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ScopeRepository scopeRepository;
    private final DisciplineRepository disciplineRepository;
    private final ApprovalTypeRepository approvalTypeRepository;
    private final EducationLevelRepository educationLevelRepository;
    private final LanguageProficiencyRepository languageProficiencyRepository;

    private final EntityMapper mapper;

    public void buildRelations(
            MainEntity entity,
            CreateDto createDto,
            List<Long> newTaskIds
    ) {
        entity.setTaskDisciplineActionLinks(buildTaskDisciplineActionLinks(entity, createDto, newTaskIds));
        entity.setResponsibilities(buildResponsibilities(createDto));
        entity.setRoleScopeDisciplineLinks(buildRoleScopeDisciplineLinks(entity, createDto));
        entity.setApprovers(buildApprovers(entity, createDto));
        entity.setEducationLevels(buildEducationLevels(entity, createDto));
        entity.setExperiences(buildExperiences(entity, createDto));
        entity.setLanguageProficiencies(buildLanguageProficiencies(entity, createDto));
    }

    private List<TaskDisciplineActionLink> buildTaskDisciplineActionLinks(
            MainEntity entity,
            CreateDto createDto,
            List<Long> newTaskIds
    ) {
        if (isEmpty(createDto.newTasks()) && isEmpty(createDto.existingTasks())) {
            return emptyList();
        }

        List<TaskDisciplineActionLink> links = new ArrayList<>();
        if (!isEmpty(createDto.newTasks())) {
            processTasks(
                    entity,
                    createDto.newTasks(),
                    dto -> ((NewTaskDto) dto).getActionIds(),
                    dto -> newTaskIds.get(createDto.newTasks().indexOf(dto)),
                    links
            );
        }

        if (!isEmpty(createDto.existingTasks())) {
            processTasks(
                    entity,
                    createDto.existingTasks(),
                    dto -> ((TaskDto) dto).actionIds(),
                    dto -> ((TaskDto) dto).id(),
                    links
            );
        }

        return links;
    }

    private List<Responsibility> buildResponsibilities(CreateDto createDto) {
        if (isEmpty(createDto.responsibilities())) {
            return emptyList();
        }

        Set<Long> ids = collectResponsibilityIds(createDto.responsibilities());
        return responsibilityRepository.findAllById(ids);
    }

    private List<RoleScopeDisciplineLink> buildRoleScopeDisciplineLinks(MainEntity entity, CreateDto createDto) {
        if (isEmpty(createDto.roles())) {
            return emptyList();
        }

        List<RoleScopeDisciplineLink> links = new ArrayList<>();
        createDto.roles().forEach(role -> {
            Role r = roleRepository.findById(role.roleId())
                    .orElseGet(() -> {
                        log.warn("Role '{}' not found", role.roleId());
                        return null;
                    });
            Scope s = scopeRepository.findById(role.scopeId())
                    .orElseGet(() -> {
                        log.warn("Scope '{}' not found", role.scopeId());
                        return null;
                    });
            Discipline d = disciplineRepository.findById(role.disciplineId())
                    .orElseThrow(() -> new NotFoundException("Discipline", role.disciplineId()));
            links.add(mapper.map(entity, r, s, d));
        });

        return links;
    }

    private List<EntityApprover> buildApprovers(MainEntity entity, CreateDto createDto) {
        if (isEmpty(createDto.approvers())) {
            return emptyList();
        }

        List<EntityApprover> approvers = new ArrayList<>();
        createDto.approvers().forEach(dto -> {
            ApprovalType type = approvalTypeRepository.findByCode(dto.code())
                    .orElseThrow(() -> new NotFoundException("ApprovalType", dto.code()));

            Long userId = Optional.of(dto)
                    .map(ApproverDto::userInfo)
                    .map(PositionDto::getPeople)
                    .filter(p -> !p.isEmpty())
                    .map(p -> p.getFirst().id())
                    .orElseThrow(() -> new AppException("User ID not found"));

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User", userId));

            approvers.add(mapper.map(entity, type, user));
        });

        return approvers;
    }

    private List<EntityEducationLevel> buildEducationLevels(MainEntity entity, CreateDto createDto) {
        if (isNull(createDto.requirements())) {
            return emptyList();
        }

        List<EntityEducationLevel> list = new ArrayList<>();
        createDto.requirements().education().forEach(ed -> {
            EducationLevel level = educationLevelRepository.findById(ed.levelId())
                    .orElseThrow(() -> new NotFoundException("EducationLevel", ed.levelId()));
            list.add(mapper.map(entity, level, ed.count()));
        });

        return list;
    }

    private List<EntityExperience> buildExperiences(MainEntity entity, CreateDto createDto) {
        if (isNull(createDto.requirements())) {
            return emptyList();
        }

        List<EntityExperience> list = new ArrayList<>();
        createDto.requirements().work().forEach(w ->
                list.add(mapper.map(entity, w.type(), w.area(), w.length()))
        );

        return list;
    }

    private List<EntityLanguageProficiency> buildLanguageProficiencies(MainEntity entity, CreateDto createDto) {
        if (isNull(createDto.requirements())) {
            return emptyList();
        }

        LanguageLevelDto lang = createDto.requirements().language();
        Long minId = lang.minLevelId();
        Long targetId = lang.targetLevelId();

        List<LanguageProficiency> found = languageProficiencyRepository.findAllById(List.of(minId, targetId));
        Map<Long, LanguageProficiency> map = found.stream()
                .collect(Collectors.toMap(LanguageProficiency::getId, Function.identity()));

        if (map.size() < 2) {
            Long missing = map.containsKey(minId) ? targetId : minId;
            throw new NotFoundException("LanguageProficiency", missing);
        }

        return List.of(
                mapper.map(entity, map.get(minId), "MIN"),
                mapper.map(entity, map.get(targetId), "TARGET")
        );
    }

    private void processTasks(
            MainEntity entity,
            List<?> dtos,
            Function<Object, List<Long>> actionExtractor,
            ToLongFunction<Object> taskIdExtractor,
            List<TaskDisciplineActionLink> result
    ) {
        dtos.forEach(dto -> {
            List<Action> actions = actionRepository.findAllById(actionExtractor.apply(dto));
            taskDisciplineLinkRepository
                    .findByTaskId(taskIdExtractor.applyAsLong(dto))
                    .forEach(link ->
                            actions.forEach(action ->
                                    result.add(mapper.map(entity, action, link))
                            )
                    );
        });
    }

    private Set<Long> collectResponsibilityIds(List<ResponsibilityTreeDto> dtos) {
        Set<Long> ids = new HashSet<>();
        dtos.forEach(dto -> {
            ids.add(dto.id());
            ids.addAll(collectResponsibilityIds(dto.children()));
        });
        return ids;
    }
  
}
