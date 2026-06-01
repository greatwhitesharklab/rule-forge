package com.ruleforge.console.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Configuration
public class RestTemplateConfig {

    @Value("${ruleforge.exec.url}")
    private String execUrl;

    @Bean
    public RestTemplate execRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RestClient execRestClient() {
        FormHttpMessageConverter formHttpMessageConverter = new FormHttpMessageConverter();
        formHttpMessageConverter.setCharset(StandardCharsets.UTF_8);
        return RestClient.builder()
                .baseUrl(this.execUrl)
                .build();
    }
}
