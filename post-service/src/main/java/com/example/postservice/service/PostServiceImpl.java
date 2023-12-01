package com.example.postservice.service;

import com.example.commonservice.config.ValidationConfig;
import com.example.commonservice.enumeration.Role;
import com.example.commonservice.response.CategorySubCategoryResponse;
import com.example.commonservice.response.FileResponse;
import com.example.commonservice.response.UserInfoResponse;
import com.example.postservice.config.FileStorageProperties;
import com.example.postservice.enums.Filter;
import com.example.postservice.exception.NotFoundExceptionClass;
import com.example.postservice.model.Post;
import com.example.postservice.repository.PostRepository;
import com.example.postservice.request.PostRequest;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.postservice.response.PostResponse;
import com.example.commonservice.model.User;
import com.example.commonservice.response.ApiResponse;
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
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final FileStorageProperties fileStorageProperties;
    @Qualifier("UserClient")
    private final WebClient.Builder userClient;
    @Qualifier("CategoryClient")
    private final WebClient.Builder categoryClient;
    @Qualifier("UserInfoClient")
    private final WebClient.Builder userInfoClient;

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;


    public PostServiceImpl(PostRepository postRepository, FileStorageProperties fileStorageProperties, @Qualifier("UserClient") WebClient.Builder userClient, @Qualifier("CategoryClient") WebClient.Builder categoryClient, @Qualifier("UserInfoClient") WebClient.Builder userInfoClient, Keycloak keycloak) {
        this.postRepository = postRepository;
        this.fileStorageProperties = fileStorageProperties;
        this.userClient = userClient;
        this.categoryClient = categoryClient;
        this.userInfoClient = userInfoClient;
        this.keycloak = keycloak;
    }

    @Override
    public PostResponse createPost(PostRequest postRequest) throws Exception {
        isNotVerify(UUID.fromString(currentUser()));
        isLegal(UUID.fromString(currentUser()));
        if(!postRequest.getSubCategory().equalsIgnoreCase(categoriesList(postRequest.getSubCategory()))){
            throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_SUB_CATEGORIES);
        }
        if(postRequest.getBudgetFrom() > postRequest.getBudgetTo()){
            throw new IllegalArgumentException(ValidationConfig.CANNOT_SMALLER);
        }
        if(postRequest.getFile() != null){
            validateFile(postRequest.getFile());
        }
        return postRepository.save(postRequest.toEntity(UUID.fromString(currentUser()))).toDto(createdBy(UUID.fromString(currentUser())), getUserInfoById(UUID.fromString(currentUser())));
    }

    @Override
    public FileResponse saveListFile(MultipartFile file, HttpServletRequest request) throws Exception {
        isNotVerify(UUID.fromString(currentUser()));
        isLegal(UUID.fromString(currentUser()));
        String uploadPath = fileStorageProperties.getUploadPath();

        Path directoryPath = Paths.get(uploadPath).toAbsolutePath().normalize();
        File directory = directoryPath.toFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String fileName = UUID.randomUUID() + file.getOriginalFilename().replaceAll("\\s+","");
        File dest = new File(directoryPath.toFile(), fileName);
        file.transferTo(dest);
        validateFile(fileName);
        return new FileResponse(
            fileName,
            file.getContentType(),
            file.getSize()
        );
    }

    @Override
    public List<PostResponse> getAllPost() {
        List<PostResponse> posts = postRepository.findAllPosts().stream().map(post -> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).toList();
        if(!posts.isEmpty()){
            return posts;
        }
        throw new NotFoundExceptionClass(ValidationConfig.POST_NOT_CONTAIN);
    }

    @Override
    public PostResponse getPostById(UUID id) {
        Post post = postRepository.findPostById(id);
        if(post != null){
            return postRepository.findPostById(id).toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()));
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public Void deletePostById(UUID id) {

        isNotVerify(UUID.fromString(currentUser()));
        isLegal(UUID.fromString(currentUser()));
        Optional<Post> post = postRepository.findById(id);
        if(post.isPresent()){
            // Create new object to store before delete
            PostResponse response = getPostById(id);
            if(response.getCreatedBy().getId().toString().equalsIgnoreCase(currentUser())){
                postRepository.deleteById(id);
                return null;
            }
            throw new IllegalArgumentException(ValidationConfig.NOT_OWNER_POST);
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public PostResponse updatePostById(UUID id, PostRequest postRequest) {
        isNotVerify(UUID.fromString(currentUser()));
        Optional<Post> pre = postRepository.findById(id);
        if(pre.isPresent()){
            Post preData = pre.get();
            if(currentUser().equalsIgnoreCase(preData.getUserId().toString())){
                isLegal(UUID.fromString(currentUser()));
                if(postRequest.getBudgetFrom() > postRequest.getBudgetTo()){
                    throw new IllegalArgumentException(ValidationConfig.CANNOT_SMALLER);
                }
                // Update Previous Data
                preData.setTitle(postRequest.getTitle());
                preData.setFile(postRequest.getFile());
                preData.setDescription(postRequest.getDescription());
                preData.setBudgetFrom(postRequest.getBudgetFrom());
                preData.setBudgetTo(postRequest.getBudgetTo());
                preData.setStatus(postRequest.getStatus());
                preData.setLastModified(LocalDateTime.now());
                preData.setSubCategory(postRequest.getSubCategory());
                return postRepository.save(preData).toDto(createdBy(UUID.fromString(currentUser())),getUserInfoById(UUID.fromString(currentUser())));
            }
            throw new NotFoundExceptionClass(ValidationConfig.NOT_OWNER_POST);
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public List<PostResponse> getAllDraftPosts() {
        isNotVerify(UUID.fromString(currentUser()));
        isLegal(UUID.fromString(currentUser()));
        List<PostResponse> draftList = postRepository.getAllDraftPosts(UUID.fromString(currentUser())).stream().map(post -> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).toList();
        if(!draftList.isEmpty()){
            return draftList;
        }
        throw new NotFoundExceptionClass(ValidationConfig.UR_POST_LIST_NOT_CONTAIN);
    }

    @Override
    public PostResponse getDraftedPostById(UUID id) {
        isNotVerify(UUID.fromString(currentUser()));
        isLegal(UUID.fromString(currentUser()));
        Post post = postRepository.findDraftedPostById(id);
        if(post != null){
            return postRepository.findDraftedPostById(id).toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()));
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public ByteArrayResource getImage(String fileName) throws IOException {
        String filePath = "post-service/src/main/resources/storage/" + fileName;
        Path path = Paths.get(filePath);
        if(!Files.exists(path)){
            throw new NotFoundExceptionClass(ValidationConfig.FILE_NOTFOUND);
        }
        String uploadPath = fileStorageProperties.getUploadPath();
        Path paths = Paths.get(uploadPath + fileName);
        return new ByteArrayResource(Files.readAllBytes(paths));
    }

    @Override
    public List<PostResponse> findByBudgetFromAndBudgetTo(Float budgetFrom, Float budgetTo) {
        if(budgetFrom < 0 || budgetTo < 0){
            throw new IllegalArgumentException(ValidationConfig.INVALID_RANGE);
        }
        if(budgetFrom > budgetTo){
            throw new IllegalArgumentException(ValidationConfig.CANNOT_SMALLER);
        }
        List<Post> posts = postRepository.findByBudgetFromAndBudgetTo(budgetFrom, budgetTo);
        if(!posts.isEmpty()){
            return posts.stream().map(post-> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).collect(Collectors.toList());
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public List<PostResponse> getAllPostSortedByNewest() {
        List<Post> posts = postRepository.findAllSortedByNewest();
        if(!posts.isEmpty()){
            return posts.stream().map(post -> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).collect(Collectors.toList());
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public List<PostResponse> getAllPostSortedByOldest() {
        List<Post> posts = postRepository.findAllSortedByOldest();
        if(!posts.isEmpty()){
            return posts.stream().map(post -> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).collect(Collectors.toList());
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public List<PostResponse> getAllPostSortedByAlphabet(Filter filter) {
        if(filter.name().equalsIgnoreCase(Filter.AZ.name())){
            List<PostResponse> AZ = postRepository.findAllSortedByAZ().stream().map(post -> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).collect(Collectors.toList());
            if(!AZ.isEmpty()){
                return AZ;
            }
        }else if (filter.name().equalsIgnoreCase(Filter.ZA.name())){
            List<PostResponse> ZA = postRepository.findAllSortedByZA().stream().map(post -> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).collect(Collectors.toList());
            if(!ZA.isEmpty()){
                return ZA;
            }
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public List<PostResponse> searchPostBySubCategory(List<String> filter) {
        List<PostResponse> responses = new ArrayList<>();
        for (String subCategory: filter) {
            List<Post> eachSub = postRepository.getAllPostSortedBySubCategory(subCategory);
            responses.addAll(eachSub.stream().map(post-> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).toList());
        }
        if (responses.isEmpty()){
            throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
        }
        return responses;
    }

    @Override
    public List<PostResponse> getPostsForCurrentUser() {
        isNotVerify(UUID.fromString(currentUser()));
        List<Post> posts = postRepository.findAllPostForCurrentUser(UUID.fromString(currentUser()));
        if(!posts.isEmpty()){
            return posts.stream().map(post-> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).collect(Collectors.toList());
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public List<PostResponse> randomPostBySubCategory(String subCategory) {
        List<Post> posts = postRepository.getAllPostSortedBySubCategory(subCategory);
        if(!posts.isEmpty()){
            // Random 3 posts
            Collections.shuffle(posts);
            List<Post> randomThreePosts = posts.subList(0, Math.min(posts.size(), 3));
            return randomThreePosts.stream().map(post-> post.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).collect(Collectors.toList());
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
    }

    @Override
    public List<PostResponse> filterRequest(List<String> subCategory, Float budgetFrom, Float budgetTo) {
        List<Post> eachSub = new ArrayList<>();
        if(budgetFrom < 0 || budgetTo < 0){
            throw new IllegalArgumentException(ValidationConfig.INVALID_RANGE);
        }
        if(budgetFrom > budgetTo){
            throw new IllegalArgumentException(ValidationConfig.CANNOT_SMALLER);
        }
        List<PostResponse> responses = new ArrayList<>();
        for (String subCategories: subCategory) {
            eachSub.addAll(postRepository.getAllPostSortedBySubCategory(subCategories));
        }
        for (Post post: eachSub) {
            if(Objects.equals(post.getBudgetFrom(), budgetFrom)){
                responses.addAll(eachSub.stream().map(p-> p.toDto(createdBy(post.getUserId()),getUserInfoById(post.getUserId()))).toList());
            }
        }
        if (responses.isEmpty()){
            throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_POST);
        }
        return responses;
    }

    @Override
    public List<PostResponse> getAllPostByUserId(UUID id) {
        List<Post> postList = postRepository.getPostByUserId(id);
        if(!postList.isEmpty()){
            return postList.stream().map(post-> post.toDto(createdBy(id),getUserInfoById(post.getUserId()))).collect(Collectors.toList());
        }
        throw new NotFoundExceptionClass(ValidationConfig.POST_NOTFOUND);
    }

    // Returning Token
    public String currentUser() {
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
        try{
            return covertSpecificClass.convertValue(Objects.requireNonNull(userClient
//                    .baseUrl("http://8.222.225.41:8081/")
                    .build()
                    .get()
                    .uri("api/v1/users/{id}", id)
                    .retrieve()
                    .bodyToMono(ApiResponse.class)
                    .block()).getPayload(), User.class);
        }catch (Exception e){
            throw new NotFoundExceptionClass(ValidationConfig.NOTFOUND_USER);
        }
    }

    // Validation legal Role
    public void isLegal(UUID id){
        if(!createdBy(id).getLoggedAs().equalsIgnoreCase(String.valueOf(Role.BUYER))){
            throw new IllegalArgumentException(ValidationConfig.ILLEGAL_PROCESS);
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

    // Account not yet verify
    public void isNotVerify(UUID id){
        UserRepresentation user = keycloak.realm(realm).users().get(String.valueOf(id)).toRepresentation();
        if(!user.getAttributes().get("is_verify").get(0).equalsIgnoreCase("true")){
            throw new IllegalArgumentException(ValidationConfig.ILLEGAL_USER);
        }
    }

    // Return User
    public String getUserInfoById(UUID id){
        ObjectMapper covertSpecificClass = new ObjectMapper();
        covertSpecificClass.registerModule(new JavaTimeModule());
        try{
            return covertSpecificClass.convertValue(Objects.requireNonNull(userInfoClient
//                    .baseUrl("http://8.222.225.41:8084/")
                    .build()
                    .get()
                    .uri("api/v1/user-info/{userId}", id)
                    .retrieve()
                    .bodyToMono(ApiResponse.class)
                    .block()).getPayload(), UserInfoResponse.class).getProfileImage();
        }catch (Exception e){
            return null;
        }
    }

}
