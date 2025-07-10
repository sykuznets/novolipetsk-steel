package com.example.budget.services;

import static java.time.LocalDate.now;

import com.example.budget.mappers.BudgetMapper;
import com.example.budget.repositories.BudgetRepository;
import com.example.budget.repositories.PendingTaskRepository;
import com.example.budget.models.Department;
import com.example.budget.models.DepartmentBudget;
import com.example.budget.models.PendingTask;
import com.example.budget.models.Position;
import com.example.budget.models.PositionBudget;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BudgetCalculationService implements BudgetService {

    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final PendingTaskRepository pendingTaskRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetMapper budgetMapper;

    @Override
    @Transactional
    public void calculateAndSaveBudgets() {
        List<String> taskKeys = pendingTaskRepository.findPendingBudgetTasks()
                .orElse(Collections.emptyList())
                .stream()
                .map(PendingTask::getKey)
                .toList();

        taskKeys.forEach(key -> {
            Department department = departmentService.getDepartment(key);
            if (department != null) {
                Set<Position> positions = fetchAllDepartmentPositions(department);
                computeAndSaveBudget(department, positions);
            }
        });

        if (!taskKeys.isEmpty()) {
            pendingTaskRepository.deleteByKeyIn(taskKeys);
        }
    }

    private Set<Position> fetchAllDepartmentPositions(Department department) {
        return Stream.iterate(department, Objects::nonNull, Department::getParent)
                .map(d -> positionService.findActivePositionsByDepartment(d.getId()))
                .filter(Optional::isPresent)
                .flatMap(opt -> opt.get().stream())
                .collect(Collectors.toSet());
    }

    private void computeAndSaveBudget(Department department, Set<Position> positions) {
        int totalPositions = positions.size();

        long missingPlanCount = countMissingBudgetEntries(positions, PositionBudget::getPlanAmount);
        long missingFactCount = countMissingBudgetEntries(positions, PositionBudget::getActualAmount);

        BigDecimal totalPlan = (double) missingPlanCount / totalPositions > 0.2
                ? BigDecimal.ZERO
                : positions.stream()
                        .flatMap(position -> position.getBudgets().stream())
                        .map(PositionBudget::getPlanAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalActual = (double) missingFactCount / totalPositions > 0.2
                ? BigDecimal.ZERO
                : positions.stream()
                        .flatMap(position -> position.getBudgets().stream())
                        .map(PositionBudget::getActualAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal variance = totalPlan.subtract(totalActual);

        DepartmentBudget budget = budgetMapper.map(
                department,
                now(),
                totalPlan,
                totalActual,
                variance
        );
        budgetRepository.save(budget);
    }

    private long countMissingBudgetEntries(
            Set<Position> positions,
            Function<PositionBudget, BigDecimal> amountExtractor
    ) {
        return positions.stream()
                .filter(position -> position.getBudgets().stream()
                        .map(amountExtractor)
                        .allMatch(Objects::isNull))
                .count();
    }

}
