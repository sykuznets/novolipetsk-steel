package com.example.apigateway.config.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.apigateway.exception.ResponseModificationException;
import com.example.apigateway.permission.util.Constants.Attribute;
import com.example.apigateway.processor.ResponseProcessor;
import com.example.apigateway.dto.UserRoleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static com.example.apigateway.util.Constants.Attribute.RESPONSE_PROCESSOR;
import static com.example.apigateway.util.Constants.Attribute.SKIP_RESPONSE_MODIFICATION;
import static com.example.apigateway.util.Constants.Attribute.USER_ROLES;
import static com.example.apigateway.util.PermissionUtils.getMatchedPermissionsFromRoles;
import static com.example.apigateway.util.RouteUtils.extractBytesFromDataBuffer;
import static java.lang.Boolean.TRUE;
import static org.springframework.core.io.buffer.DataBufferUtils.join;
import static org.springframework.core.io.buffer.DataBufferUtils.release;

/**
 * This filter intercepts the response from the service and modifies it
 * based on the user's roles and permissions: it nullifies all fields of the response object
 * except those that are present in the list of permissions. If the attribute SKIP_RESPONSE_MODIFICATION
 * is set to true, the filter skips the modification of the original response.
 * <p>
 * Required attributes: {@link Attribute#USER_ROLES} and {@link Attribute#RESPONSE_PROCESSOR}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResponseModificationFilter implements GatewayFilter {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (TRUE.equals(exchange.getAttribute(SKIP_RESPONSE_MODIFICATION))) {
            return chain.filter(exchange);
        }

        if (exchange.getAttribute(RESPONSE_PROCESSOR) instanceof ResponseProcessor responseProcessor) {
            ServerHttpResponseDecorator decoratedResponse = createResponseDecorator(
                exchange.getResponse(),
                exchange.getAttribute(USER_ROLES),
                responseProcessor::process
            );

            return chain.filter(
                exchange.mutate()
                    .response(decoratedResponse)
                    .build()
            );
        } else {
            throw new IllegalArgumentException("Invalid or missing response processor");
        }
    }

    private ServerHttpResponseDecorator createResponseDecorator(
        ServerHttpResponse originalResponse,
        List<UserRoleDto> roles,
        BiConsumer<Set<String>, Map<String, Object>> responseProcessor
    ) {
        return new ServerHttpResponseDecorator(originalResponse) {
            @Override
            @Nonnull
            public Mono<Void> writeWith(@Nonnull Publisher<? extends DataBuffer> body) {
                log.debug("Starting to modify response body...");
                
                return join(body).flatMap(dataBuffer -> {
                    byte[] originalBytes = extractBytesFromDataBuffer(dataBuffer);
                    release(dataBuffer);

                    try {
                        Set<String> permissions = getMatchedPermissionsFromRoles(roles);
                        log.debug("Matched permissions: '{}'", permissions);

                        Map<String, Object> responseMap = objectMapper.readValue(
                            originalBytes,
                            new TypeReference<>() {}
                        );

                        responseProcessor.accept(permissions, responseMap);

                        byte[] modifiedBytes = objectMapper.writeValueAsBytes(responseMap);
                        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                        DataBuffer modifiedBuffer = bufferFactory.wrap(modifiedBytes);
                        originalResponse.getHeaders().setContentLength(modifiedBytes.length);

                        log.debug("Response modified successfully, new length: '{}'", modifiedBytes.length);
                        return super.writeWith(Mono.just(modifiedBuffer));

                    } catch (Exception e) {
                        return Mono.error(new ResponseModificationException(
                            "Failed to modify response body: " + e.getMessage()
                        ));
                    }
                });
            }
        };
    }
}
