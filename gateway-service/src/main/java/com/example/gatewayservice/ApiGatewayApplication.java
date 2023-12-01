package com.example.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.CrossOrigin;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

//    @Bean
//    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
//        return builder
//
//                .routes()
//
//                .route(r -> r.path("/gateway-service/v3/api-docs").uri("lb://gateway-service"))
//
//                .route(r -> r.path("/category-service/v3/api-docs").uri("lb://category-service"))
//                .route(r -> r.path("/api/v1/categories/**").uri("lb://category-service"))
//                .route(r -> r.path("/api/v1/sub-categories/**").uri("lb://category-service"))
//
//                .route(r -> r.path("/user-service/v3/api-docs").uri("lb://user-service"))
//                .route(r -> r.path("/api/v1/users/**").uri("lb://user-service"))
//                .route(r -> r.path("/api/v1/third-party/**").uri("lb://user-service"))
//
//                .route(r -> r.path("/chat-service/v3/api-docs").uri("lb://chat-service"))
//                .route(r -> r.path("/api/v1/chats/**").uri("lb://chat-service"))
//
//                .route(r -> r.path("/post-service/v3/api-docs").uri("lb://post-service"))
//                .route(r -> r.path("/api/v1/posts/**").uri("lb://post-service"))
//                .route(r -> r.path("/api/v1/operation/**").uri("lb://post-service"))
//
//                .route(r -> r.path("/product-service/v3/api-docs").uri("lb://product-service"))
//                .route(r -> r.path("/api/v1/products/**").uri("lb://product-service"))
//
//                .route(r -> r.path("/shop-service/v3/api-docs").uri("lb://shop-service"))
//                .route(r -> r.path("/api/v1/shops/**").uri("lb://shop-service"))
//                .route(r -> r.path("/api/v1/ratings/**").uri("lb://shop-service"))
//
//                .route(r -> r.path("/user-info-service/v3/api-docs").uri("lb://user-info-service"))
//                .route(r -> r.path("/api/v1/user-info/**").uri("lb://user-info-service"))
//                .route(r -> r.path("/api/v1/seller-favorite/**").uri("lb://user-info-service"))
//                .route(r -> r.path("/api/v1/buyer-favorite/**").uri("lb://user-info-service"))
//
//                .build();
//    }
}
