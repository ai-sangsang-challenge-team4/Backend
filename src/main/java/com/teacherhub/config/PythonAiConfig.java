package com.teacherhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PythonAiConfig {

    @Bean
    public RestClient pythonAiRestClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:8000")
                .build();
    }
}