package com.example.productservice.config;

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
    @Value("${shopUrl}")
    private String shopUrl;
    @Value("${postUrl}")
    private String postUrl;

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

    @Bean("ShopClient")
    @LoadBalanced
    public WebClient.Builder shopClient() {
        return WebClient.builder().baseUrl(shopUrl);
    }
    @Bean("PostClient")
    @LoadBalanced
    public WebClient.Builder postClient() {
        return WebClient.builder().baseUrl(postUrl);
    }

}
