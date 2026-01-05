package com.example.file.service.impl;

import com.example.file.service.FileProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FileProxyServiceImpl implements FileProxyService {

    private final WebClient fileProxyWebClient;

    @Override
    public Mono<ResponseEntity<byte[]>> retrieveFile(String environment, String encodedPath) {
        return fileProxyWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + environment)
                        .queryParam("encodedPath", encodedPath)
                        .build())
                .retrieve()
                .bodyToMono(byte[].class)
                .map(fileBytes -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"downloaded_file.pdf\"")
                        .body(fileBytes));
    }

}
