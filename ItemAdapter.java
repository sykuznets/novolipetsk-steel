package adapters;

import adapters.repositories.GenericRepository;
import adapters.repositories.GenericViewedItemRepository;
import domain.model.Role;
import domain.model.Item;
import domain.model.primitives.DateRange;
import domain.operations.enums.SortingOrder;
import domain.operations.enums.SortingType;
import domain.ports.ItemPort;
import java.time.Clock;
import java.time.LocalDate;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.criteria.Predicate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import static java.time.OffsetDateTime.now;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemAdapter implements ItemPort {

    private static final String PRIORITY = "priority";
    private static final String PUBLISHED = "published";

    private final GenericRepository<ItemRecord, Long> itemRepository;
    private final GenericViewedItemRepository viewedItemRepository;
    private final Mapper mapper;
    private final Clock clock;

    @Override
    public Item create(@NonNull Item item) {
        var itemRecord = mapper.map(item);
        itemRecord.setCreated(now(clock));
        return mapper.map(itemRepository.save(itemRecord));
    }

    @Override
    public Item getItem(Long userId, Long itemId) {
        log.info("Fetching item with userId: '{}' and itemId: '{}'", userId, itemId);

        var matchingItemRecord = itemRepository.findById(itemId);
        if (matchingItemRecord.isEmpty()) {
            return new Item();
        }

        var item = mapper.map(matchingItemRecord.get());
        var viewedItemRecord = viewedItemRepository.getViewedItem(userId, itemId);

        item.setViewed(viewedItemRecord.isPresent() &&
                matchingItemRecord.get().getId().equals(viewedItemRecord.get().getItemId()));

        return item;
    }

    @Override
    public void setItemViewed(@NonNull Item.ItemId id, Long userId) {
        log.info("Setting item as viewed, itemId: '{}', userId: '{}'", id, userId);

        var itemRecord = itemRepository.findById(id.getValue()).orElse(null);
        if (isNull(itemRecord)) {
            log.warn("Item not found, itemId: '{}', userId: '{}'", id, userId);
            return;
        }

        var newViewedItemRecord = new UserViewedItemRecord();
        newViewedItemRecord.setItemId(itemRecord.getId());
        newViewedItemRecord.setUserId(userId);

        viewedItemRepository.save(newViewedItemRecord);

        log.info("Viewed item record saved, itemId: '{}', userId: '{}'", itemRecord.getId(), userId);
    }

    @Override
    public SimpleEntry<List<Item>, Long> retrieveAdminItems(
            Integer from,
            Integer count,
            SortingType type,
            SortingOrder order,
            DateRange filterByPublishedDate,
            List<Role> roleFilter,
            List<Integer> filterByFacilityCode
    ) {
        Specification<ItemRecord> spec = Specification.where(null)
                .and(addPublishedDateFilter(filterByPublishedDate))
                .and(addRoleFilter(roleFilter))
                .and(addFacilityCodeFilter(filterByFacilityCode));

        var sortedRequest = createSortedRequest(type, order, from, count);
        var itemPage = itemRepository.findAll(spec, sortedRequest);

        return new SimpleEntry<>(mapper.map(itemPage.getContent()), itemPage.getTotalElements());
    }

    private Specification<ItemRecord> addPublishedDateFilter(DateRange filterByPublishedDate) {
        if (isNull(filterByPublishedDate)
                || filterByPublishedDate.isEmpty()) {
            return Specification.where(null);
        }

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (nonNull(filterByPublishedDate.getFrom())) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        criteriaBuilder.function("DATE", LocalDate.class, root.get(PUBLISHED)),
                        filterByPublishedDate.getFrom()));
            }
            if (nonNull(filterByPublishedDate.getTo())) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        criteriaBuilder.function("DATE", LocalDate.class, root.get(PUBLISHED)),
                        filterByPublishedDate.getTo()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ItemRecord> addRoleFilter(List<Role> roleFilter) {
        if (isNull(roleFilter) || roleFilter.isEmpty()) return Specification.where(null);

        return (root, query, criteriaBuilder) -> criteriaBuilder.or(roleFilter.stream()
                .map(role -> criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("roles").as(String.class)),
                        "%" + role.toString().toLowerCase() + "%"
                ))
                .toArray(Predicate[]::new));
    }

    private Specification<ItemRecord> addFacilityCodeFilter(List<Integer> filterByFacilityCode) {
        if (isNull(filterByFacilityCode)
                || filterByFacilityCode.isEmpty()) {
            return Specification.where(null);
        }

        return (root, query, criteriaBuilder) -> criteriaBuilder.or(filterByFacilityCode.stream()
                .map(facilityCode -> criteriaBuilder.or(
                        criteriaBuilder.isTrue(criteriaBuilder.function(
                                "array_position",
                                Integer.class,
                                root.get("facilityCodes"),
                                criteriaBuilder.literal(facilityCode)
                        ).isNotNull()),
                        criteriaBuilder.isNull(root.get("facilityCodes"))
                ))
                .toArray(Predicate[]::new));
    }

    private PageRequest createSortedRequest(
            Integer from,
            Integer count,
            SortingType type,
            SortingOrder order
    ) {
        Sort sort = determineSortOrder(type, order);
        return PageRequest.of((from == null ? 0 : from) / count, count, sort);
    }

    private Sort determineSortOrder(SortingType type, SortingOrder order) {
        if (isNull(type)) {
            return "desc".equalsIgnoreCase(order.toString())
                    ? Sort.by(PRIORITY).descending().and(Sort.by(PUBLISHED).descending())
                    : Sort.by(PRIORITY).ascending().and(Sort.by(PUBLISHED).descending());
        }
        if (PUBLISHED.equalsIgnoreCase(type.toString())) {
            return "asc".equalsIgnoreCase(order.toString())
                    ? Sort.by(PUBLISHED).ascending()
                    : Sort.by(PUBLISHED).descending();
        }
        if (PRIORITY.equalsIgnoreCase(type.toString())) {
            return "desc".equalsIgnoreCase(order.toString())
                    ? Sort.by(PRIORITY).descending().and(Sort.by(PUBLISHED).descending())
                    : Sort.by(PRIORITY).ascending().and(Sort.by(PUBLISHED).descending());
        }

        return Sort.unsorted();
    }

}
