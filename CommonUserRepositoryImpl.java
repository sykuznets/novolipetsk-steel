package com.example.common.repository.custom;

import static org.apache.commons.collections.CollectionUtils.isEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.common.domain.User;
import com.example.common.domain.User_;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.jpa.SpecHints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CommonRepositoryImpl implements CommonRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public Page<String> fetchUserIds(Specification<User> specification, Pageable pageableRequest) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> tupleQuery = criteriaBuilder.createTupleQuery();

        Root<User> userRoot = tupleQuery.from(User.class);
        Map<String, Join<?, ?>> joinRegistry = new HashMap<>();

        if (specification != null) {
            Predicate filterPredicate = specification.toPredicate(userRoot, tupleQuery, criteriaBuilder);
            if (filterPredicate != null) {
                tupleQuery.where(filterPredicate);
            }
        }

        tupleQuery.multiselect(userRoot.get(User_.empId));
        tupleQuery.orderBy(prepareOrders(pageableRequest.getSort(), userRoot, joinRegistry, criteriaBuilder));

        List<Tuple> queryResult = entityManager.createQuery(tupleQuery)
                .setFirstResult((int) pageableRequest.getOffset())
                .setMaxResults(pageableRequest.getPageSize())
                .getResultList();

        List<String> userIdList = queryResult.stream()
                .map(tuple -> tuple.get(0, String.class))
                .toList();

        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<User> countRoot = countQuery.from(User.class);
        countQuery.select(criteriaBuilder.countDistinct(countRoot.get(User_.empId)));

        if (specification != null) {
            Predicate countPredicate = specification.toPredicate(countRoot, countQuery, criteriaBuilder);
            if (countPredicate != null) {
                countQuery.where(countPredicate);
            }
        }

        Long totalCount = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(userIdList, pageableRequest, totalCount);
    }

    private Path<?> resolveJoinPath(
            String propertyPath,
            Root<User> userRoot,
            Map<String, Join<?, ?>> joinRegistry
    ) {
        if (!propertyPath.contains(".")) {
            return userRoot.get(propertyPath);
        }

        String[] pathSegments = propertyPath.split("\\.");
        From<?, ?> currentJoin = userRoot;

        StringBuilder joinKeyBuilder = new StringBuilder();

        for (int i = 0; i < pathSegments.length - 1; i++) {
            joinKeyBuilder.append(joinKeyBuilder.isEmpty() ? pathSegments[i] : "." + pathSegments[i]);
            String joinKey = joinKeyBuilder.toString();

            final From<?, ?> resolvedCurrent = currentJoin;
            int index = i;
            currentJoin = joinRegistry.computeIfAbsent(
                    joinKey,
                    k -> resolvedCurrent.join(pathSegments[index], JoinType.LEFT)
            );
        }

        return currentJoin.get(pathSegments[pathSegments.length - 1]);
    }

    private List<Order> prepareOrders(
            Sort sort,
            Root<User> userRoot,
            Map<String, Join<?, ?>> joinRegistry,
            CriteriaBuilder criteriaBuilder
    ) {
        List<Order> orderList = new ArrayList<>();
        if (sort.isSorted()) {
            for (Sort.Order sortOrder : sort) {
                Path<?> orderPath = resolveJoinPath(sortOrder.getProperty(), userRoot, joinRegistry);
                orderList.add(sortOrder.isAscending() ? criteriaBuilder.asc(orderPath) : criteriaBuilder.desc(orderPath));
            }
        }

        orderList.add(criteriaBuilder.asc(userRoot.get(User_.empId)));
        return orderList;
    }
    
}
