package com.example.auth.controller;

import static com.example.auth.util.AuthClientConstants.DEFAULT_RETURN_URI;
import static com.example.auth.util.AuthClientConstants.RETURN_URL_ATTRIBUTE;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/public/auth")
@Slf4j
public class AuthRedirectController {

    @Value("${app.auth.authorization-url}")
    private String authorizationBaseUrl;

    @GetMapping("/entry")
    public Mono<Void> authEntry(ServerWebExchange exchange) {
        String requestUri = exchange.getRequest().getHeaders().getFirst("X-Request-URI");
        log.info("Received original URI: '{}'", requestUri);

        if (requestUri == null || requestUri.isBlank()) {
            requestUri = DEFAULT_RETURN_URI;
        }

        String encodedUri = URLEncoder.encode(requestUri, StandardCharsets.UTF_8);
        log.info("Unauthenticated request for '{}', redirecting to auth provider", requestUri);

        String targetRedirect = authorizationBaseUrl + encodedUri;
        log.info("Redirect target: '{}'", targetRedirect);

        String finalUri = requestUri;
        return exchange.getSession()
            .flatMap(session -> {
                session.getAttributes().put(RETURN_URL_ATTRIBUTE, finalUri);
                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                exchange.getResponse().getHeaders().setLocation(URI.create(targetRedirect));
                return exchange.getResponse().setComplete();
            });
    }
  
}
