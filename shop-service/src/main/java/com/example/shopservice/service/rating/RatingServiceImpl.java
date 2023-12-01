package com.example.shopservice.service.rating;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.commonservice.config.ValidationConfig;
import com.example.commonservice.enumeration.Role;
import com.example.commonservice.model.User;
import com.example.commonservice.response.ApiResponse;
import com.example.commonservice.response.CategorySubCategoryResponse;
import com.example.commonservice.response.SubCategoryResponse;
import com.example.shopservice.enumeration.Level;
import com.example.shopservice.exception.NotFoundExceptionClass;
import com.example.shopservice.exception.NullExceptionClass;
import com.example.shopservice.model.Rating;
import com.example.shopservice.model.Shop;
import com.example.shopservice.repository.RatingRepository;
import com.example.shopservice.repository.ShopRepository;
import com.example.shopservice.request.RatingRequest;
import com.example.shopservice.response.RatingResponse;
import com.example.shopservice.response.ShopResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
//@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final RatingRepository ratingRepository;
    private final ShopRepository service;
    @Qualifier("UserClient")
    private final WebClient.Builder userClient;
    @Qualifier("CategoryClient")
    private final WebClient.Builder categoryClient;
    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public RatingServiceImpl(RatingRepository ratingRepository, ShopRepository service, WebClient.Builder userClient, WebClient.Builder categoryClient, Keycloak keycloak) {
        this.ratingRepository = ratingRepository;
        this.service = service;
        this.userClient = userClient;
        this.categoryClient = categoryClient;
        this.keycloak = keycloak;
    }

    @Override
    public RatingResponse ratingShop(RatingRequest request) {

        isNotVerify(UUID.fromString(currentUser()));
        isLegal(UUID.fromString(currentUser()));

        if(request.getLevel() == null){
            throw new NullExceptionClass("level"+ValidationConfig.NULL_INPUT);
        }else if(request.getShopId() == null){
            throw new NullExceptionClass("shop id"+ValidationConfig.NULL_INPUT);
        }

        Optional<Shop> shop = service.findById(request.getShopId());
        if(shop.isPresent()){
            if(!shop.get().getStatus()){
                throw new IllegalArgumentException(ValidationConfig.INACTIVE_SHOP);
            }
            Rating rating = ratingRepository.getRatingRecordByOwnerId(createdBy(UUID.fromString(currentUser())).getId(), request.getShopId());
            if(rating != null){
                rating.setLevel(request.getLevel());
                return ratingRepository.save(rating).toDto(request.getShopId());
            }
            return ratingRepository.save(request.toEntity(createdBy(UUID.fromString(currentUser())).getId(),shop.get())).toDto(request.getShopId());
        }
        throw new NotFoundExceptionClass(ValidationConfig.SHOP_NOTFOUND);
    }

    @Override
    public List<ShopResponse> getRatedShopByCurrentId() {
        isLegal(UUID.fromString(currentUser()));
        List<Rating> ratings = ratingRepository.findAll();
        if(!ratings.isEmpty()){
            List<ShopResponse> shops = new ArrayList<>();
            for (Rating rate : ratings) {
                Shop shop = service.getActiveShopById(rate.getShop().getId());
                if(shop != null){
                    shops.add(shop.toDto(categoriesList(shop.getSubCategoryList()), ratedCount(shop.getId()), ratedPercentage(shop.getId())));
                }
            }
            if(!shops.isEmpty()){
                return shops;
            }
            throw new NotFoundExceptionClass(ValidationConfig.NOT_RATING);
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_RATING);
    }

    // Returning Token
    public String currentUser(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                // Decode to Get User Id
            DecodedJWT decodedJWT = JWT.decode(jwt.getTokenValue());
                return decodedJWT.getSubject();
            }
        }catch (Exception e){
            throw new NotFoundExceptionClass(ValidationConfig.NOTFOUND_USER);
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOTFOUND_USER);
    }

    // Return User
    public User createdBy(UUID id){
        ObjectMapper covertSpecificClass = new ObjectMapper();
        covertSpecificClass.registerModule(new JavaTimeModule());

        return covertSpecificClass.convertValue(Objects.requireNonNull(userClient
//                .baseUrl("http://8.222.225.41:8081/")
                .build()
                .get()
                .uri("api/v1/users/{id}", id)
                .retrieve()
                .bodyToMono(ApiResponse.class)
                .block()).getPayload(), User.class);
    }


    // Rated Count by Shop Id
    public Integer ratedCount(UUID shopId){
        List<String> level = ratingRepository.getRatedStarByShopId(shopId);
        return level.size();
    }

    // Percentage Rating shop
    public Float ratedPercentage(UUID shopId){
        int sum = 0;
        List<String> level = ratingRepository.getRatedStarByShopId(shopId);
        for (String star : level) {
            if(star.equalsIgnoreCase(Level.ONE_STAR.name())){
                sum += 1;
            }else if(star.equalsIgnoreCase(Level.TWO_STARS.name())){
                sum += 2;
            }else if(star.equalsIgnoreCase(Level.THREE_STARS.name())){
                sum += 3;
            }else if(star.equalsIgnoreCase(Level.FOUR_STARS.name())){
                sum += 4;
            }else if(star.equalsIgnoreCase(Level.FIVE_STARS.name())){
                sum += 5;
            }
        }
        return (float) (sum/level.size());
    }

    // Validation legal Role
    public void isLegal(UUID id){
        if(!createdBy(id).getLoggedAs().equalsIgnoreCase(String.valueOf(Role.BUYER))){
            throw new IllegalArgumentException(ValidationConfig.ILLEGAL_PROCESS);
        }
    }

    // Converting Category from Attribute as String to ArrayList
    public List<String> category(String categories){
        List<String> categoriesList = Arrays.asList(categories.replaceAll("\\[|\\]", "").split(", "));
        return categoriesList.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }


    // Returning list category
    public List<String> categoriesList(String categories) {
        List<String> uuidList = category(categories);
        ObjectMapper covertSpecificClass = new ObjectMapper();
        covertSpecificClass.registerModule(new JavaTimeModule());
        List<String> responses = new ArrayList<>();
        try {
            for (String name : uuidList) {
                CategorySubCategoryResponse subName = covertSpecificClass.convertValue(Objects.requireNonNull(categoryClient
//                        .baseUrl("http://8.222.225.41:8087/")
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("api/v1/sub-categories")
                                .queryParam("name", name.toUpperCase())
                                .build())
                        .retrieve()
                        .bodyToMono(ApiResponse.class)
                        .block()).getPayload(), CategorySubCategoryResponse.class);
                responses.add(subName.getSubCategory().getName());
            }
            return responses;
        }catch (Exception e){
            throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_SUB_CATEGORIES);
        }

    }

    // Account not yet verify
    public void isNotVerify(UUID id){
        UserRepresentation user = keycloak.realm(realm).users().get(String.valueOf(id)).toRepresentation();
        if(!user.getAttributes().get("is_verify").get(0).equalsIgnoreCase("true")){
            throw new IllegalArgumentException(ValidationConfig.ILLEGAL_USER);
        }
    }

}
