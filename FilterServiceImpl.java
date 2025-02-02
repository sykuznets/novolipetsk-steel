package com.example.api.service.impl;

import com.example.api.exception.RoleException;
import com.example.api.dto.FilterDto;
import com.example.api.dto.RoleDto;
import com.example.api.service.FilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.stream.Stream.empty;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilterServiceImpl implements FilterService {

    @Override
    public List<FilterDto> getFiltersByPermission(String permissionCode, List<RoleDto> roles) {
        log.info("Fetching filters for permission: '{}'", permissionCode);

        var validRoles = verifyRolesWithPermission(permissionCode, roles);
        var filters = extractFiltersFromRoles(validRoles);

        log.info("Found '{}' filters", filters.size());
        return filters;
    }

    private List<RoleDto> verifyRolesWithPermission(
            String permissionCode,
            List<RoleDto> roles
    ) {
        List<RoleDto> rolesWithPermission = selectRolesWithPermission(permissionCode, roles);

        if (isEmpty(rolesWithPermission)) {
            String message = String.format("No user roles with permission '%s' found", permissionCode);
            log.error(message);
            throw new RoleException(message);
        }

        if (rolesWithPermission.size() > 1) {
            var roleTitles = roles.stream()
                    .map(RoleDto::getTitle)
                    .collect(Collectors.joining(", "));

            throw new RoleException(String.format(
                    "Expected a single role with permission '%s', but found: %d roles: '%s'",
                    permissionCode,
                    rolesWithPermission.size(),
                    roleTitles
            ));
        }

        return rolesWithPermission;
    }

    private List<RoleDto> selectRolesWithPermission(
            String permissionCode,
            List<RoleDto> roles
    ) {
        return roles.stream()
                .filter(role -> {
                    var permissions = role.getPermissions();
                    if (isEmpty(permissions)) {
                        log.info("No permissions found for role: '{}'", role);
                        return false;
                    }
                    return permissions.stream()
                            .anyMatch(permission -> permissionCode.equalsIgnoreCase(permission.getCode()));
                })
                .collect(Collectors.toList());
    }

    private List<FilterDto> extractFiltersFromRoles(List<RoleDto> roles) {
        if (isEmpty(roles.get(0).getGroups())) {
            return emptyList();
        }

        // Extract filters from groups and scopes
        return roles.get(0).getGroups().stream()
                .flatMap(group -> !isNull(group.getScopes()) ? group.getScopes().stream() : empty())
                .flatMap(scope -> !isNull(scope.getFilters()) ? scope.getFilters().stream() : empty())
                .collect(Collectors.toList());
    }
    
}
