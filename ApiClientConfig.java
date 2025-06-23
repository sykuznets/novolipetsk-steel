package com.example.publicapi.client.config;

import com.example.publicapi.client.properties.ApiClientProperties;
import com.example.publicapi.client.service.ApiServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApiClientConfig {

    private final ApiClientProperties apiClientProperties;

    @Bean
    public ApiServiceClient apiServiceClient() {
        ApiClientProperties.ServiceConfig serviceConfig = apiClientProperties.getServiceConfig();

        log.debug(
                "Initializing API client with base URL: '{}'",
                serviceConfig.getBaseUrl()
        );

        RestClient restClient = RestClient.builder()
                .baseUrl(serviceConfig.getBaseUrl())
                .requestFactory(createRequestFactory(serviceConfig))
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(ApiServiceClient.class);
    }

    private ClientHttpRequestFactory createRequestFactory(
            ApiClientProperties.ServiceConfig serviceConfig
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(serviceConfig.getConnectTimeout());
        factory.setReadTimeout(serviceConfig.getReadTimeout());
        return factory;
    }
    
}
