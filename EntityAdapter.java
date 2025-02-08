package adapters;

import adapters.repositories.*;
import adapters.repositories.records.ViewedEntityRecord;
import adapters.repositories.records.EntityRecord;
import domain.model.Role;
import domain.model.Entity;
import domain.model.Entity.EntityId;
import domain.model.primitives.DateRange;
import domain.operations.enums.SortingOrder;
import domain.operations.enums.SortingType;
import domain.ports.EntityPort;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.rntgroup.brain.authorization.util.AuthoritiesUtils.getCollectedRoles;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntityAdapter implements EntityPort {

    private static final String MANAGER = "manager";
    private static final String PUBLISHED = "published";
    private static final String PRIORITY = "priority";
    private static final String FACILITY_CODES = "facilityCodes";

    private final EntityRepository entityRepository;
    private final ViewedEntityRepository viewedEntityRepository;
    private final RepositoriesMapper mapper;

    @Override
    public List<Entity> getEntities(
            Long employeeId,
            List<String> roles,
            List<Integer> facilityCodes
    ) {
        List<EntityRecord> entitiesByRole = new ArrayList<>();

        var rolesByProfileId = getCollectedRoles(Collectors.toList());

        if (roles.contains(MANAGER) && rolesByProfileId.contains(MANAGER)) {
            entitiesByRole = facilityCodes == null || facilityCodes.isEmpty()
                    ? entityRepository.findEntitiesForManagerWithNoFacilityCodes()
                    : entityRepository.findEntitiesForManager(facilityCodes);

            log.info("Total entities found for manager: '{}'", entitiesByRole);

            return getEntitiesWithViewedStatus(employeeId, entitiesByRole);
        }

        log.info("Roles found for employeeId '{}': '{}'", employeeId, rolesByProfileId);

        if (rolesByProfileId.contains("specialist") || rolesByProfileId.contains(MANAGER)) {
            entitiesByRole = facilityCodes == null || facilityCodes.isEmpty()
                    ? entityRepository.findEntitiesForSpecialistWithNoFacilityCodes()
                    : entityRepository.findEntitiesForSpecialist(facilityCodes);
        }

        if (rolesByProfileId.contains("employee")) {
            entitiesByRole = facilityCodes == null || facilityCodes.isEmpty()
                    ? entityRepository.findEntitiesForEmployeeWithNoFacilityCodes()
                    : entityRepository.findEntitiesForEmployee(facilityCodes);
        }

        log.info("Total entities found: '{}'", entitiesByRole);
        return getEntitiesWithViewedStatus(employeeId, entitiesByRole);
    }

    @Override
    public void setEntityViewed(@NonNull EntityId id, Long employeeId) {
        log.info("Setting entity as viewed, entityId: '{}', employeeId: '{}'", id, employeeId);

        var entityRecord = entityRepository
                .findById(id.getValue())
                .orElse(null);

        if (isNull(entityRecord)) {
            log.warn("Entity not found, entityId: '{}', employeeId: '{}'", id, employeeId);
            return;
        }

        var newViewedEntityRecord = new ViewedEntityRecord();
        newViewedEntityRecord.setEntityId(entityRecord.getId());
        newViewedEntityRecord.setEmployeeId(employeeId);

        viewedEntityRepository.save(newViewedEntityRecord);
        log.info(
                "Viewed entity record saved, entityId: '{}', employeeId: '{}'",
                entityRecord.getId(),
                employeeId
        );
    }

    @Override
    public SimpleEntry<List<Entity>, Long> retrieveAdminEntities(
            Integer from,
            Integer count,
            SortingType type,
            SortingOrder order,
            DateRange filterByPublishedDate,
            List<Role> roleFilter,
            List<Integer> filterByFacilityCode
    ) {
        Specification<EntityRecord> spec = Specification.where(null);

        spec = addPublishedDateFilter(filterByPublishedDate, spec);
        spec = addRoleFilter(spec, roleFilter);
        spec = addFacilityCodeFilter(spec, filterByFacilityCode);

        var sortedRequest = createSortedRequest(type, order, from, count);

        var entityPage = entityRepository.findAll(spec, sortedRequest);

        return new SimpleEntry<>(
                mapper.map(entityPage.getContent()),
                entityPage.getTotalElements()
        );
    }

    private List<Entity> getEntitiesWithViewedStatus(
            Long employeeId,
            List<EntityRecord> entities
    ) {
        var entitiesIds = entities.stream()
                .map(EntityRecord::getId)
                .toList();

        var viewedIds = viewedEntityRepository
                .findViewedEntityIdsByEmployeeId(entitiesIds, employeeId)
                .orElse(emptyList());

        log.info("Viewed IDs found for entity IDs: '{}'", viewedIds);

        return entities.stream()
                .map(entityRecord -> {
                    var entity = mapper.map(entityRecord);
                    entity.setViewed(viewedIds.contains(entityRecord.getId()));
                    return entity;
                })
                .toList();
    }

    private Specification<EntityRecord> addPublishedDateFilter(
            DateRange filterByPublishedDate,
            Specification<EntityRecord> spec
    ) {
        if (nonNull(filterByPublishedDate) && !filterByPublishedDate.isEmpty()) {
            return spec.and((root, query, criteriaBuilder) -> {
                List<Predicate> predicates = new ArrayList<>();
                if (nonNull(filterByPublishedDate.getFrom())) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                            criteriaBuilder.function("DATE", LocalDate.class, root.get(PUBLISHED)),
                            filterByPublishedDate.getFrom()
                    ));
                }
                if (nonNull(filterByPublishedDate.getTo())) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(
                            criteriaBuilder.function("DATE", LocalDate.class, root.get(PUBLISHED)),
                            filterByPublishedDate.getTo()
                    ));
                }

                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            });
        }

        return spec;
    }

    private Specification<EntityRecord> addRoleFilter(
            Specification<EntityRecord> spec,
            List<Role> roleFilter
    ) {
        if (nonNull(roleFilter) && !roleFilter.isEmpty()) {
            return spec.and((root, query, criteriaBuilder) -> {
                List<Predicate> predicates = roleFilter.stream()
                        .map(role -> criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("roles").as(String.class)),
                                "%" + role.toString().toLowerCase() + "%"
                        ))
                        .toList();

                return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
            });
        }

        return spec;
    }

    private Specification<EntityRecord> addFacilityCodeFilter(
            Specification<EntityRecord> spec,
            List<Integer> filterByFacilityCode
    ) {
        if (nonNull(filterByFacilityCode) && !filterByFacilityCode.isEmpty()) {
            return spec.and((root, query, criteriaBuilder) -> {
                List<Predicate> predicates = filterByFacilityCode.stream()
                        .map(facilityCode -> criteriaBuilder.or(
                                criteriaBuilder.isTrue(criteriaBuilder.function(
                                        "array_position",
                                        Integer.class,
                                        root.get(FACILITY_CODES),
                                        criteriaBuilder.literal(facilityCode)
                                ).isNotNull()),
                                criteriaBuilder.isNull(root.get(FACILITY_CODES))
                        ))
                        .toList();

                return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
            });
        }

        return spec;
    }

    private PageRequest createSortedRequest(
            SortingType type,
            SortingOrder order,
            Integer from,
            Integer count
    ) {
        String sortBy = null;
        String orderDirection = null;

        if (nonNull(type)) {
            sortBy = type.toString();
        }
        if (nonNull(order)) {
            orderDirection = order.toString();
        }

        var sort = Sort.unsorted();

        if (isNull(type)) {
            sort = "desc".equalsIgnoreCase(orderDirection)
                    ? Sort.by(PRIORITY).descending().and(Sort.by(PUBLISHED).descending())
                    : Sort.by(PRIORITY).ascending().and(Sort.by(PUBLISHED).descending());
        } else if (PUBLISHED.equalsIgnoreCase(sortBy)) {
            sort = "asc".equalsIgnoreCase(orderDirection)
                    ? Sort.by(PUBLISHED).ascending()
                    : Sort.by(PUBLISHED).descending();
        } else if (PRIORITY.equalsIgnoreCase(sortBy)) {
            sort = "desc".equalsIgnoreCase(orderDirection)
                    ? Sort.by(PRIORITY).descending().and(Sort.by(PUBLISHED).descending())
                    : Sort.by(PRIORITY).ascending().and(Sort.by(PUBLISHED).descending());
        }

        if (isNull(from)) {
            from = 0;
        }

        return PageRequest.of(from / count, count, sort);
    }

}
