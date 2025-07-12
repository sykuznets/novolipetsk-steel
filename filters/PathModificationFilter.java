package com.example.gateway.config.filter;

import com.example.gateway.config.ApplicationConfig;
import com.example.gateway.config.RouteConfig;
import com.example.gateway.exception.PermissionException;
import com.example.gateway.processor.impl.PageResponseProcessor;
import com.example.gateway.client.RoleBasedPermissionClient;
import com.example.gateway.dto.FilterDto;
import com.example.gateway.service.FilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.example.gateway.util.Constants.Attribute.EXPAND_QUERY_PARAM;
import static com.example.gateway.util.Constants.Attribute.RESPONSE_PROCESSOR;
import static com.example.gateway.util.Constants.Attribute.SKIP_RESPONSE_MODIFICATION;
import static com.example.gateway.util.Constants.Attribute.USER_ROLES;
import static com.example.gateway.util.RouteUtils.buildModifiedPath;
import static com.example.gateway.util.RouteUtils.getUserId;
import static com.example.gateway.util.RouteUtils.handlePermissionException;

/**
 * This filter modifies the request path to the appropriate API endpoint.
 * It retrieves the user's roles and, based on those roles, adjusts the request path
 * by applying role-based filters. If no filters are found, the request is not modified.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PathModificationFilter implements GatewayFilterFactory<RouteConfig> {

    private final RoleBasedPermissionClient roleClient;
    private final FilterService filterService;
    private final ApplicationConfig applicationConfig;

    @Override
    public GatewayFilter apply(RouteConfig config) {
        return (exchange, chain) -> Mono.justOrEmpty(getUserId(exchange))
            .flatMap(userId -> roleClient.retrieveUserRoles(userId, applicationConfig.getRoleBasedPermissions().getUser())
                .flatMap(roles -> {
                    if (roles.isEmpty()) {
                        return Mono.error(new PermissionException("User '" + userId + "' has no roles assigned"));
                    }

                    var filters = filterService.getFiltersByPermission(config.permission(), roles);
                    if (filters.isEmpty()) {
                        exchange.getAttributes().put(SKIP_RESPONSE_MODIFICATION, true);
                        return chain.filter(exchange);
                    }

                    exchange.getAttributes().put(USER_ROLES, roles);
                    exchange.getAttributes().put(RESPONSE_PROCESSOR, new PageResponseProcessor());

                    var uri = UriComponentsBuilder.fromUriString(
                        buildModifiedPath(exchange, applicationConfig.getMboLibrary(), filters)
                    ).build().toUri();

                    var updatedExchange = exchange.mutate()
                        .request(exchange.getRequest().mutate().uri(uri).build())
                        .build();

                    return chain.filter(updatedExchange);
                }))
            .switchIfEmpty(chain.filter(exchange))
            .onErrorResume(PermissionException.class, ex -> handlePermissionException(getUserId(exchange), exchange, ex));
    }
    
}
