package com.example.postservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${userUrl}")
    private String userUrl;
    @Value("${categoryUrl}")
    private String categoryUrl;
    @Value("${userInfoUrl}")
    private String userInfoUrl;

    @Bean("UserClient")
    @LoadBalanced
    public WebClient.Builder userClient() {
        return WebClient.builder().baseUrl(userUrl);
    }
    @Bean("CategoryClient")
    @LoadBalanced
    public WebClient.Builder categoryClient() {
        return WebClient.builder().baseUrl(categoryUrl);
    }
    @Bean("UserInfoClient")
    @LoadBalanced
    public WebClient.Builder userInfoClient() {
        return WebClient.builder().baseUrl(userInfoUrl);
    }



}
