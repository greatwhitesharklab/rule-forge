package com.ruleforge.executor.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Configuration
public class RestTemplateConfig {

    @Value("${ruleforge.console.url}")
    private String consoleUrl;

    @Bean
    public RestTemplate consoleRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        FormHttpMessageConverter formHttpMessageConverter = new FormHttpMessageConverter();
        formHttpMessageConverter.setCharset(StandardCharsets.UTF_8);
        restTemplate.getMessageConverters().add(formHttpMessageConverter);
        return restTemplate;
    }

    @Bean
    public RestClient consoleRestClient() {
        return RestClient.builder()
                .baseUrl(this.consoleUrl)
                .build();
    }
}
