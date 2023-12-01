package com.example.productservice.service.product;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.commonservice.config.ValidationConfig;
import com.example.commonservice.enumeration.Role;
import com.example.commonservice.model.User;
import com.example.commonservice.response.ApiResponse;
import com.example.commonservice.response.CategorySubCategoryResponse;
import com.example.commonservice.response.ShopResponse;
import com.example.productservice.config.FileStorageProperties;
import com.example.productservice.exception.NotFoundExceptionClass;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.request.ProductRequest;
import com.example.productservice.response.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
//@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService{

    private final ProductRepository productRepository;
    private final FileStorageProperties fileStorageProperties;
    @Qualifier("UserClient")
    private final WebClient.Builder userClient;
    @Qualifier("CategoryClient")
    private final WebClient.Builder categoryClient;
    @Qualifier("ShopClient")
    private final WebClient.Builder shopClient;
    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public ProductServiceImpl(ProductRepository productRepository, FileStorageProperties fileStorageProperties, @Qualifier("UserClient") WebClient.Builder userClient, @Qualifier("CategoryClient") WebClient.Builder categoryClient, @Qualifier("ShopClient") WebClient.Builder shopClient, Keycloak keycloak) {
        this.productRepository = productRepository;
        this.fileStorageProperties = fileStorageProperties;
        this.userClient = userClient;
        this.categoryClient = categoryClient;
        this.shopClient = shopClient;
        this.keycloak = keycloak;
    }

    @Override
    public ProductResponse saveListFile(UUID productId, List<MultipartFile> files, HttpServletRequest request) throws IOException {
        isNotVerify(UUID.fromString(currentUser()));
        UUID shopId = shop(UUID.fromString(currentUser())).getId();

        Optional<Product> pre = productRepository.findById(productId);

        if(pre.isEmpty()){
            throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_PRODUCT);
        }
        if(files.isEmpty()){
            throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_FILE);
        }
        Product preData = pre.get();

        if(!shopId.toString().equalsIgnoreCase(preData.getShopId().toString())){
            throw new IllegalArgumentException(ValidationConfig.CANNOT_UPLOAD);
        }

        isLegal(UUID.fromString(currentUser()));
        List<String> listFiles = new ArrayList<>();



        for (MultipartFile file : files) {
            String uploadPath = fileStorageProperties.getUploadPath();
            Path directoryPath = Paths.get(uploadPath).toAbsolutePath().normalize();
            File directory = directoryPath.toFile();

            if (!directory.exists()) {
                directory.mkdirs();
            }

            String fileName = UUID.randomUUID() + file.getOriginalFilename().replaceAll("\\s+","");
            File dest = new File(directoryPath.toFile(), fileName);
            file.transferTo(dest);
            listFiles.add(fileName);
        }
        preData.setFile(listFiles.toString());
        return productRepository.save(preData).toDto(listFiles);
    }

    @Override
    public ProductResponse addProduct(ProductRequest postRequest) throws Exception {
        isNotVerify(UUID.fromString(currentUser()));
        isLegal(UUID.fromString(currentUser()));
        for (String image : postRequest.getFiles()) {
            validateFile(image);
        }
        categoriesList(postRequest.getSubCategory());
        return productRepository.save(postRequest.toEntity(shop(UUID.fromString(currentUser())).getId())).toDto(postRequest.getFiles());
    }

    // This method need to update for related products
    @Override
    public List<ProductResponse> getAllProduct() {
        List<ProductResponse> responseList = productRepository.findAll().stream().map(product -> product.toDto(getFiles(product))).collect(Collectors.toList());
        if(!responseList.isEmpty()){
            return responseList;
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_PRODUCTS);
    }

    @Override
    public ProductResponse getProductById(UUID id) {

        Optional<Product> product = productRepository.findById(id);
        if(product.isPresent()){
            return product.get().toDto(getFiles(productRepository.findById(id).orElseThrow()));
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_PRODUCT);

    }

    @Override
    public ProductResponse deleteProductById(UUID id) {
        isNotVerify(UUID.fromString(currentUser()));
        UUID shopId = shop(UUID.fromString(currentUser())).getId();
        Product preData = productRepository.findById(id).orElseThrow();
        if(shopId.toString().equalsIgnoreCase(preData.getShopId().toString())){
            isLegal(UUID.fromString(currentUser()));
            // Create new object to store before delete
            ProductResponse response = getProductById(id);
            productRepository.deleteById(id);
            return response;
        }
       throw new IllegalArgumentException(ValidationConfig.CANNOT_DELETE);
    }

    @Override
    public ProductResponse updateProductById(UUID id, ProductRequest request) throws Exception {
        isNotVerify(UUID.fromString(currentUser()));
        Product preData = productRepository.findById(id).orElseThrow();

        for (String image : request.getFiles()) {
            validateFile(image);
        }

        UUID shopId = shop(UUID.fromString(currentUser())).getId();
        if(shopId.toString().equalsIgnoreCase(preData.getShopId().toString())){
            isLegal(UUID.fromString(currentUser()));
            // Update Previous Data
            preData.setTitle(request.getTitle());
            preData.setFile(request.getFiles().toString());
            preData.setDescription(request.getDescription());
            preData.setPrice(request.getPrice());
            preData.setQuantity(request.getQuantity());
            preData.setLastModified(LocalDateTime.now());
            return productRepository.save(preData).toDto(getFiles(preData));
        }
        throw new IllegalArgumentException(ValidationConfig.CANNOT_UPDATE);

    }

    @Override
    public List<ProductResponse> getAllProductByShopId(UUID id) {
        ShopResponse shop = shopById(id);
        if(shop != null){
            List<ProductResponse> responseList = productRepository.getAllProductByShopId(id).stream().map(product -> product.toDto(getFiles(product))).collect(Collectors.toList());
            if(!responseList.isEmpty()){
                return responseList;
            }
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_PRODUCTS_IN_UR_SHOP);
    }

    @Override
    public ByteArrayResource getImage(String fileName) throws IOException {
        String filePath = "product-service/src/main/resources/storage/" + fileName;
        Path path = Paths.get(filePath);

        if(!Files.exists(path)){
            throw new NotFoundExceptionClass(ValidationConfig.FILE_NOTFOUND);
        }
        String uploadPath = fileStorageProperties.getUploadPath();
        Path paths = Paths.get(uploadPath + fileName);
        return new ByteArrayResource(Files.readAllBytes(paths));
    }

    // Returning Token
    public String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            DecodedJWT decodedJWT = JWT.decode(jwt.getTokenValue());
            return decodedJWT.getSubject();
        } else {
            return null;
        }
    }

    // Returning list category
    public String categoriesList(String categories) {
        ObjectMapper covertSpecificClass = new ObjectMapper();
        covertSpecificClass.registerModule(new JavaTimeModule());
        try {
            CategorySubCategoryResponse subName = covertSpecificClass.convertValue(Objects.requireNonNull(categoryClient
//                    .baseUrl("http://8.222.225.41:8087/")
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("api/v1/sub-categories")
                            .queryParam("name", categories.toUpperCase())
                            .build())
                    .retrieve()
                    .bodyToMono(ApiResponse.class)
                    .block()).getPayload(), CategorySubCategoryResponse.class);
            return subName.getSubCategory().getName();
        }catch (Exception e){
            throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_SUB_CATEGORIES);
        }
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

    // Return Shop
    public ShopResponse shop(UUID userId){
        ObjectMapper covertSpecificClass = new ObjectMapper();
        covertSpecificClass.registerModule(new JavaTimeModule());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try{
            if(authentication.getPrincipal() instanceof Jwt jwt){
                return covertSpecificClass.convertValue(Objects.requireNonNull(shopClient
//                        .baseUrl("http://localhost:8088/")
                        .build()
                        .get()
                        .uri("api/v1/shops/user/{userId}", userId)
                        .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                        .retrieve()
                        .bodyToMono(ApiResponse.class)
                        .block()).getPayload(), ShopResponse.class);
            }
        }catch (Exception e){
            throw new IllegalArgumentException(ValidationConfig.SHOP_NOT_CREATED);
        }
        throw new IllegalArgumentException(ValidationConfig.SHOP_NOT_CREATED);
    }

    // Return Shop
    public ShopResponse shopById(UUID id){
        ObjectMapper covertSpecificClass = new ObjectMapper();
        covertSpecificClass.registerModule(new JavaTimeModule());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try{
            if(authentication.getPrincipal() instanceof Jwt jwt){
                return covertSpecificClass.convertValue(Objects.requireNonNull(shopClient
//                        .baseUrl("http://localhost:8088/")
                        .build()
                        .get()
                        .uri("api/v1/shops/{id}", id)
                        .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                        .retrieve()
                        .bodyToMono(ApiResponse.class)
                        .block()).getPayload(), ShopResponse.class);
            }

        }catch (Exception e){
            throw new IllegalArgumentException(ValidationConfig.SHOP_NOTFOUND);
        }
        throw new IllegalArgumentException(ValidationConfig.SHOP_NOTFOUND);
    }

    // Separate file -> List
    private List<String> getFiles(Product product) {
        return Arrays.asList(product.getFile().replaceAll(ValidationConfig.REGEX_ROLES, "").split(", "));
    }

    // Validation legal Role
    public void isLegal(UUID id){
        if(!createdBy(id).getLoggedAs().equalsIgnoreCase(String.valueOf(Role.SELLER))){
            throw new IllegalArgumentException(ValidationConfig.ILLEGAL_PROCESS);
        }
    }

    // Account not yet verify
    public void isNotVerify(UUID id){
        UserRepresentation user = keycloak.realm(realm).users().get(String.valueOf(id)).toRepresentation();
        if(!user.getAttributes().get("is_verify").get(0).equalsIgnoreCase("true")){
            throw new IllegalArgumentException(ValidationConfig.ILLEGAL_USER);
        }
    }

    // Validation String image
    public static void validateFile(String fileName) throws Exception {
        String[] validExtensions = {".jpg", ".jpeg", ".png", ".tiff"};
        boolean isValidExtension = false;
        for (String extension : validExtensions) {
            if (fileName.toLowerCase().endsWith(extension)) {
                isValidExtension = true;
                break;
            }
        }
        if (!isValidExtension) {
            throw new IllegalArgumentException(ValidationConfig.ILLEGAL_FILE);
        }
    }




}
