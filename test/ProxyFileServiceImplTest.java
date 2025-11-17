package com.example.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.service.impl.ProxyFileServiceImpl;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProxyFileServiceImplTest {

    @Mock
    private WebClient webClient;
    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private ProxyFileServiceImpl proxyFileService;

    @Test
    @SuppressWarnings("unchecked")
    void testRetrieveFile() {
        String environment = "test-environment";
        String encodedFilePath = "encoded-file-path";
        byte[] sampleFileContent = "Sample file content".getBytes();

        ResponseEntity<byte[]> expectedResponse = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document.pdf\"")
                .body(sampleFileContent);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(byte[].class)).thenReturn(Mono.just(sampleFileContent));

        // Act
        Mono<ResponseEntity<byte[]>> actualResult = proxyFileService.retrieveFile(environment, encodedFilePath);

        StepVerifier.create(actualResult)
                .expectNext(expectedResponse)
                .verifyComplete();
    }

}
