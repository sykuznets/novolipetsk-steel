package com.example.model;

import am.ik.yavi.builder.ValidatorBuilder;
import am.ik.yavi.constraint.ObjectConstraint;
import am.ik.yavi.core.Validator;
import com.example.exceptions.ValidationException;
import com.example.annotations.ExcludeFromJacocoGeneratedReport;
import java.io.Serializable;

/**
 * Represents a generic entity with validation logic applied through YAVI.
 */
public record ProjectTarget(
    Integer id,
    Integer systemId,
    Long createdAt,
    Long ownerId,
    Integer year,
    Boolean deleted
) implements Serializable {

  private static final Validator<ProjectTarget> VALIDATOR = ValidatorBuilder.<ProjectTarget>of()
      ._object(ProjectTarget::id, "id", ObjectConstraint::notNull)
      ._object(ProjectTarget::systemId, "systemId", ObjectConstraint::notNull)
      ._object(ProjectTarget::createdAt, "createdAt", ObjectConstraint::notNull)
      ._object(ProjectTarget::ownerId, "ownerId", ObjectConstraint::notNull)
      ._object(ProjectTarget::year, "year", ObjectConstraint::notNull)
      ._object(ProjectTarget::deleted, "deleted", ObjectConstraint::notNull)
      .build();

  @ExcludeFromJacocoGeneratedReport
  public static ProjectTarget validated(
      Integer id,
      Integer systemId,
      Long createdAt,
      Long ownerId,
      Integer year,
      Boolean deleted
  ) {
    var target = new ProjectTarget(id, systemId, createdAt, ownerId, year, deleted);
    var violations = VALIDATOR.validate(target);
    if (violations.isValid()) {
      return target;
    }

    throw new ValidationException(target, violations);
  }
  
}
