package com.example.util;

import com.example.config.ServiceConfig;
import com.example.exception.AccessException;
import com.example.dto.CriteriaDto;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.util.Constants.Operators.IN;
import static com.example.util.HeaderUtils.extractUserDetails;
import static com.example.util.UrlUtils.addCriteriaToPath;
import static java.util.AbstractMap.SimpleEntry;
import static org.springframework.util.StringUtils.isEmpty;

@UtilityClass
@Slf4j
public class RouteUtils {

    public static String buildRequestPath(
            ServerWebExchange exchange,
            ServiceConfig.EndpointConfig endpointConfig,
            List<CriteriaDto> criteria
    ) {
        String basePath = endpointConfig.getUrl() + exchange.getRequest().getURI().getPath();
        String query = exchange.getRequest().getURI().getQuery();
        String pathWithQuery = !isEmpty(query) ? basePath + "?" + query : basePath;

        String finalPathWithCriteria = addCriteriaToPath(pathWithQuery, createCriteriaMap(criteria));

        log.debug("Generated request path: '{}'", finalPathWithCriteria);
        
        return finalPathWithCriteria;
    }

    public static Map<String, SimpleEntry<String, Object>> createCriteriaMap(List<CriteriaDto> criteria) {
        Map<String, SimpleEntry<String, Object>> criteriaMap = new HashMap<>();

        criteria.forEach(criterion -> {
            String operator = criterion.operator().toLowerCase();
            String field = criterion.field().substring(criterion.field().lastIndexOf('/') + 1);
            Object value = operator.equals(IN) ? List.of(criterion.value()) : criterion.value();

            criteriaMap.put(field, new SimpleEntry<>(operator, value));
        });

        return criteriaMap;
    }

    public static Mono<Void> handleAccessDenied(
            String userId,
            ServerWebExchange exchange,
            AccessException ex
    ) {
        log.error("Access denied for userId '{}': '{}'", userId, ex.getMessage());

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        var bytes = ex.getMessage().getBytes();

        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(bytes)
        ));
    }

    public static String getUserIdFromRequest(ServerWebExchange exchange) {
        var headers = exchange.getRequest().getHeaders();
        var userDetails = extractUserDetails(headers);
        String userId = userDetails.getUserId();

        if (isEmpty(userId)) {
            log.warn("User ID not found in request headers");
            return null;
        }

        return userId;
    }

    public static byte[] extractBytes(DataBuffer dataBuffer) {
        byte[] content = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(content);
        return content;
    }
    
}
