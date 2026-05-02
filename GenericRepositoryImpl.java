package com.example.repository.custom;

import static org.apache.commons.collections.CollectionUtils.isEmpty;

import java.util.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.jpa.SpecHints;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GenericUserRepositoryImpl implements GenericUserRepositoryCustom {

    private final EntityManager entityManager;

    private static final String EMP_ID = "empId";

    @Override
    public Page<String> findIds(Specification<?> specification, Pageable pageRequest) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> criteriaQuery = criteriaBuilder.createTupleQuery();

        Root<?> entityRoot = criteriaQuery.from(Object.class);
        Map<String, Join<?, ?>> joinMap = new HashMap<>();

        if (specification != null) {
            Predicate predicate = ((Specification<Object>) specification)
                    .toPredicate(entityRoot, criteriaQuery, criteriaBuilder);
            if (predicate != null) {
                criteriaQuery.where(predicate);
            }
        }

        criteriaQuery.multiselect(entityRoot.get(EMP_ID));
        criteriaQuery.orderBy(createOrders(pageRequest.getSort(), entityRoot, joinMap, criteriaBuilder));

        List<Tuple> tuples = entityManager.createQuery(criteriaQuery)
                .setFirstResult((int) pageRequest.getOffset())
                .setMaxResults(pageRequest.getPageSize())
                .getResultList();

        List<String> ids = tuples.stream()
                .map(t -> t.get(0, String.class))
                .toList();

        CriteriaQuery<Long> countCriteria = criteriaBuilder.createQuery(Long.class);
        Root<?> countRoot = countCriteria.from(Object.class);
        countCriteria.select(criteriaBuilder.countDistinct(countRoot.get(EMP_ID)));

        if (specification != null) {
            Predicate countPredicate = ((Specification<Object>) specification)
                    .toPredicate(countRoot, countCriteria, criteriaBuilder);
            if (countPredicate != null) {
                countCriteria.where(countPredicate);
            }
        }

        Long total = entityManager.createQuery(countCriteria).getSingleResult();
        return new PageImpl<>(ids, pageRequest, total);
    }

    @Override
    public List<Object> findAllByIdsWithGraph(Collection<String> ids, List<String> attributes) {
        if (isEmpty(ids)) {
            return List.of();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> cq = cb.createQuery(Object.class);

        Root<Object> root = cq.from(Object.class);
        cq.select(root).where(root.get(EMP_ID).in(ids));

        TypedQuery<Object> query = entityManager.createQuery(cq);

        if (!isEmpty(attributes)) {
            EntityGraph<Object> graph = entityManager.createEntityGraph(Object.class);
            attributes.forEach(graph::addAttributeNodes);
            query.setHint(SpecHints.HINT_SPEC_LOAD_GRAPH, graph);
        }

        return query.getResultList();
    }

    private Path<?> resolveAttributePath(
            String property,
            Root<Object> root,
            Map<String, Join<?, ?>> joinMap
    ) {
        if (!property.contains(".")) {
            return root.get(property);
        }

        String[] parts = property.split("\\.");
        From<?, ?> current = root;

        StringBuilder joinKeyBuilder = new StringBuilder();

        for (int i = 0; i < parts.length - 1; i++) {
            joinKeyBuilder.append(joinKeyBuilder.isEmpty() ? parts[i] : "." + parts[i]);
            String joinKey = joinKeyBuilder.toString();

            final From<?, ?> finalCurrent = current;
            int index = i;

            current = joinMap.computeIfAbsent(
                    joinKey,
                    k -> finalCurrent.join(parts[index], JoinType.LEFT)
            );
        }

        return current.get(parts[parts.length - 1]);
    }

    private List<Order> createOrders(
            Sort sort,
            Root<Object> root,
            Map<String, Join<?, ?>> joinMap,
            CriteriaBuilder cb
    ) {
        List<Order> orders = new ArrayList<>();

        if (sort.isSorted()) {
            for (Sort.Order order : sort) {
                Path<?> path = resolveAttributePath(order.getProperty(), root, joinMap);
                orders.add(order.isAscending() ? cb.asc(path) : cb.desc(path));
            }
        }

        orders.add(cb.asc(root.get(EMP_ID)));
        return orders;
    }
}
