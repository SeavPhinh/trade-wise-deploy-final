package com.example.shopservice.config;

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

    @Bean("userClient")
    @LoadBalanced
    public WebClient.Builder userClient() {
        return WebClient.builder().baseUrl(userUrl);
    }

    @Bean("categoryClient")
    @LoadBalanced
    public WebClient.Builder categoryClient() {
        return WebClient.builder().baseUrl(categoryUrl);
    }

//    @Bean
//    public WebClient loadBalancedWebClient(WebClient.Builder webClientBuilder) {
//        return webClientBuilder.baseUrl(baseUrl).build();
//    }

}
