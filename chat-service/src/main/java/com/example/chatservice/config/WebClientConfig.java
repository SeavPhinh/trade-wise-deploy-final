package com.example.chatservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${userUrl}")
    private String userUrl;
    @Value("${userInfoUrl}")
    private String userInfoUrl;
    @Value("${shopUrl}")
    private String shopUrl;

    @Bean("UserClient")
    @LoadBalanced
    public WebClient.Builder userClient() {
        return WebClient.builder().baseUrl(userUrl);
    }
    @Bean("UserInfoClient")
    @LoadBalanced
    public WebClient.Builder userInfoClient() {
        return WebClient.builder().baseUrl(userInfoUrl);
    }
    @Bean("ShopClient")
    @LoadBalanced
    public WebClient.Builder shopClient() {
        return WebClient.builder().baseUrl(shopUrl);
    }

}
