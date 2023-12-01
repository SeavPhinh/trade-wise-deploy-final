package com.example.chatservice.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.chatservice.config.FileStorageProperties;
import com.example.chatservice.exception.NotFoundExceptionClass;
import com.example.chatservice.model.ConnectedResponse;
import com.example.chatservice.model.MessageModel;
import com.example.chatservice.model.MessageResponse;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.commonservice.config.ValidationConfig;
import com.example.commonservice.enumeration.Role;
import com.example.commonservice.model.User;
import com.example.commonservice.response.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService{

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    @Qualifier("UserClient")
    private final WebClient.Builder userClient;
    @Qualifier("UserInfoClient")
    private final WebClient.Builder userInfoClient;
    @Qualifier("ShopClient")
    private final WebClient.Builder shopClient;
    private final FileStorageProperties fileStorageProperties;
    private final Keycloak keycloak;
    @Value("${keycloak.realm}")
    private String realm;

//    @Autowired
//    public ChatServiceImpl(SimpMessagingTemplate messagingTemplate,
//                           ChatMessageRepository chatMessageRepository,
//                           WebClient.Builder userWeb,
//                           FileStorageProperties fileStorageProperties, Keycloak keycloak) {
//        this.messagingTemplate = messagingTemplate;
//        this.chatMessageRepository = chatMessageRepository;
//        this.userWeb = userWeb;
//        this.fileStorageProperties = fileStorageProperties;
//        this.keycloak = keycloak;
//    }


    public ChatServiceImpl(SimpMessagingTemplate messagingTemplate, ChatMessageRepository chatMessageRepository, @Qualifier("UserClient") WebClient.Builder userClient, @Qualifier("UserInfoClient") WebClient.Builder userInfoClient, @Qualifier("ShopClient") WebClient.Builder shopClient, FileStorageProperties fileStorageProperties, Keycloak keycloak) {
        this.messagingTemplate = messagingTemplate;
        this.chatMessageRepository = chatMessageRepository;
        this.userClient = userClient;
        this.userInfoClient = userInfoClient;
        this.shopClient = shopClient;
        this.fileStorageProperties = fileStorageProperties;
        this.keycloak = keycloak;
    }

    public void sendDirectMessage(MessageModel message){
        MessageModel userDestination = new MessageModel();
        List<MessageModel> messages = new ArrayList<>();
        for (MessageModel mess : chatMessageRepository.findAll()) {
            if(mess.getReceiverId().toString().equalsIgnoreCase(message.getSenderId().toString()) &&
                    mess.getSenderId().toString().equalsIgnoreCase(message.getReceiverId().toString()) ||
                    mess.getReceiverId().toString().equalsIgnoreCase(message.getReceiverId().toString()) &&
                            mess.getSenderId().toString().equalsIgnoreCase(message.getSenderId().toString())
            ){
                messages.add(mess);
                userDestination = messages.get(0);
            }
        }
        if(userDestination != null){
            messagingTemplate.convertAndSendToUser(userDestination.getSenderId()+"&"+userDestination.getReceiverId(), "/private", message);
        }else {
            messagingTemplate.convertAndSendToUser(message.getSenderId()+"&"+message.getReceiverId(), "/private", message);
        }
        message.setId(UUID.randomUUID());
        message.setTimestamp(LocalDateTime.now());
        message.setStatus(false);
        chatMessageRepository.persistData(message);
    }

    public List<MessageModel> getHistoryMessage(UUID connectedUser) {
        isNotVerify(UUID.fromString(currentUser()));
        String loggedAs =  createdBy(UUID.fromString(currentUser())).getLoggedAs();
        if(loggedAs.equalsIgnoreCase("BUYER")){
            List<MessageModel> model = chatMessageRepository.findHistory(connectedUser, UUID.fromString(currentUser()));
            if(!model.isEmpty()){
                return model;
            }
        }else{
            ShopResponse shopResponse = getShopByUserId(UUID.fromString(currentUser()));
            List<MessageModel> model = chatMessageRepository.findHistory(connectedUser, shopResponse.getId());
            if(!model.isEmpty()){
                return model;
            }
        }

        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_MESSAGE);
    }

    public MessageModel isContainDestination(UUID userId) {
        isNotVerify(UUID.fromString(currentUser()));
        List<MessageModel> messages = new ArrayList<>();
        String loggedAs =  createdBy(UUID.fromString(currentUser())).getLoggedAs();
        if(loggedAs.equalsIgnoreCase("SELLER")){
            ShopResponse shopResponse = getShopByUserId(UUID.fromString(currentUser()));
            for (MessageModel message : chatMessageRepository.findAll()) {
                if(message.getReceiverId().toString().equalsIgnoreCase(userId.toString()) &&
                        message.getSenderId().toString().equalsIgnoreCase(shopResponse.getId().toString()) ||
                        message.getReceiverId().toString().equalsIgnoreCase(shopResponse.getId().toString()) &&
                                message.getSenderId().toString().equalsIgnoreCase(userId.toString())
                ){
                    messages.add(message);
                    return messages.get(0);
                }
            }
        }else{
            for (MessageModel message : chatMessageRepository.findAll()) {
                if(message.getReceiverId().toString().equalsIgnoreCase(userId.toString()) &&
                        message.getSenderId().toString().equalsIgnoreCase(currentUser()) ||
                        message.getReceiverId().toString().equalsIgnoreCase(currentUser()) &&
                                message.getSenderId().toString().equalsIgnoreCase(userId.toString())
                ){
                    messages.add(message);
                    return messages.get(0);
                }
            }
        }
        return null;
    }

    public List<ConnectedResponse> getAllContactUser() {
        isNotVerify(UUID.fromString(currentUser()));
        checkChat(UUID.fromString(currentUser()));
        List<ConnectedResponse> responses = new ArrayList<>();
        List<UUID> uniqueIds = new ArrayList<>();
        List<MessageModel> listMessage = new ArrayList<>();
        String loggedAs =  createdBy(UUID.fromString(currentUser())).getLoggedAs();
        ShopResponse shopResponse = getShopByUserId(UUID.fromString(currentUser()));

        if(loggedAs.equalsIgnoreCase("SELLER")){
            listMessage = chatMessageRepository.getUserByCurrentUserId(shopResponse.getId());
        }else{
            listMessage = chatMessageRepository.getUserByCurrentUserId(UUID.fromString(currentUser()));
        }

        if(!listMessage.isEmpty()){
            for (MessageModel message : listMessage) {
                UUID senderId = message.getSenderId();
                UUID receiverId = message.getReceiverId();
                if(loggedAs.equalsIgnoreCase("BUYER")){
                    if (senderId != null && senderId.equals(UUID.fromString(currentUser())) && !uniqueIds.contains(receiverId)) {
                        uniqueIds.add(receiverId);
                    }
                    if (receiverId != null && receiverId.equals(UUID.fromString(currentUser())) && !uniqueIds.contains(senderId)) {
                        uniqueIds.add(senderId);
                    }
                }else{
                    if (senderId != null && senderId.equals(shopResponse.getId()) && !uniqueIds.contains(receiverId)) {
                        uniqueIds.add(receiverId);
                    }
                    if (receiverId != null && receiverId.equals(shopResponse.getId()) && !uniqueIds.contains(senderId)) {
                        uniqueIds.add(senderId);
                    }
                }
            }
            for (UUID id : uniqueIds) {

                ConnectedResponse connectedResponse = new ConnectedResponse();
                User user = getUserById(id);

                if(user != null){
                    UserInfoResponse userInfo = getUserInfoById(user.getId());
                    if(userInfo != null){
                        connectedResponse.setUser((new UserContact(user.getId(),user.getUsername(),userInfo.getProfileImage())));
                    }else{
                        connectedResponse.setUser((new UserContact(user.getId(),user.getUsername(),null)));
                    }
                }else{
                    ShopResponse shop = shopById(id);
                    connectedResponse.setUser((new UserContact(shop.getId(),shop.getName(),shop.getProfileImage())));
                }

                int lastIndex;
                if(!loggedAs.equalsIgnoreCase("BUYER")){
                    lastIndex = chatMessageRepository.getAllMessageWithConnectedUser(id, shopResponse.getId()).size() - 1;
                    String userType = null;
                    if(chatMessageRepository.getAllMessageWithConnectedUser(id, shopResponse.getId()).get(lastIndex).getSenderId().toString().equals(shopResponse.getId().toString())){
                        userType = "Sender";
                    }else if((chatMessageRepository.getAllMessageWithConnectedUser(id, shopResponse.getId()).get(lastIndex).getReceiverId().toString().equals(shopResponse.getId().toString()))){
                        userType = "Receiver";
                    }
                    connectedResponse.setMessage(new MessageResponse(
                            chatMessageRepository.getAllMessageWithConnectedUser(id, shopResponse.getId()).get(lastIndex),
                            userType,
                            chatMessageRepository.getAllMessageWithConnectedUser(id, shopResponse.getId()).get(lastIndex).getTimestamp()
                    ));
                    connectedResponse.setUnseenCount(chatMessageRepository.getAllUnseenMessage(shopResponse.getId()).size());
                    responses.add(connectedResponse);
                }else{
                    lastIndex = chatMessageRepository.getAllMessageWithConnectedUser(id, UUID.fromString(currentUser())).size() - 1;
                    String userType = null;
                    if(chatMessageRepository.getAllMessageWithConnectedUser(id, UUID.fromString(currentUser())).get(lastIndex).getSenderId().toString().equals(currentUser())){
                        userType = "Sender";
                    }else if((chatMessageRepository.getAllMessageWithConnectedUser(id, UUID.fromString(currentUser())).get(lastIndex).getReceiverId().toString().equals(currentUser()))){
                        userType = "Receiver";
                    }
                    connectedResponse.setMessage(new MessageResponse(
                            chatMessageRepository.getAllMessageWithConnectedUser(id, UUID.fromString(currentUser())).get(lastIndex),
                            userType,
                            chatMessageRepository.getAllMessageWithConnectedUser(id, UUID.fromString(currentUser())).get(lastIndex).getTimestamp()
                    ));
                    connectedResponse.setUnseenCount(chatMessageRepository.getAllUnseenMessage(UUID.fromString(currentUser())).size());
                    responses.add(connectedResponse);
                }
            }
            return responses;
        }
        throw new NotFoundExceptionClass(ValidationConfig.NOT_FOUND_USER_CONTACT);
    }

    @Override
    public String updateAllMessages(UUID connectedUser) {
        isNotVerify(UUID.fromString(currentUser()));
        checkChat(connectedUser);
        checkChat(UUID.fromString(currentUser()));
        String loggedAs =  createdBy(UUID.fromString(currentUser())).getLoggedAs();
        if(loggedAs.equalsIgnoreCase("BUYER")){
            chatMessageRepository.updateAllUnseenMessages(connectedUser,UUID.fromString(currentUser()));
        }else{
            ShopResponse shopResponse = getShopByUserId(UUID.fromString(currentUser()));
            chatMessageRepository.updateAllUnseenMessages(connectedUser,shopResponse.getId());
        }
        chatMessageRepository.updateAllUnseenMessages(connectedUser,UUID.fromString(currentUser()));
        return "Messages updated successfully";
    }



    // Validation if user haven't chat with other
    public void checkChat(UUID id){
        if(chatMessageRepository.getByUserId(id) == null){
            throw new NotFoundExceptionClass(ValidationConfig.NOT_YET_TEXTING);
        }
    }

    // Return User
    public User getUserById(UUID id){
        isNotVerify(UUID.fromString(currentUser()));
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
            return null;
        }
    }

    public ShopResponse getShopByUserId(UUID id){
        isNotVerify(UUID.fromString(currentUser()));
        ObjectMapper covertSpecificClass = new ObjectMapper();
        covertSpecificClass.registerModule(new JavaTimeModule());
        try{
            return covertSpecificClass.convertValue(Objects.requireNonNull(shopClient
//                    .baseUrl("http://8.222.225.41:8088/")
                    .build()
                    .get()
                    .uri("api/v1/shops/user/{id}", id)
                    .retrieve()
                    .bodyToMono(ApiResponse.class)
                    .block()).getPayload(), ShopResponse.class);
        }catch (Exception e){
            throw new NotFoundExceptionClass(ValidationConfig.SHOP_NOTFOUND);
        }
    }

    // Return Shop
    public ShopResponse shopById(UUID id){
        ObjectMapper covertSpecificClass = new ObjectMapper();
        covertSpecificClass.registerModule(new JavaTimeModule());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try{
            if(authentication.getPrincipal() instanceof Jwt jwt){
                return covertSpecificClass.convertValue(Objects.requireNonNull(shopClient
//                        .baseUrl("http://8.222.225.41:8088/")
                        .build()
                        .get()
                        .uri("api/v1/shops/{id}", id)
                        .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                        .retrieve()
                        .bodyToMono(ApiResponse.class)
                        .block()).getPayload(), ShopResponse.class);
            }

        }catch (Exception e){
            throw new IllegalArgumentException("Contact now found");
        }
        throw new IllegalArgumentException("Contact now found");
    }

    // Return User
    public UserInfoResponse getUserInfoById(UUID id){
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
                    .block()).getPayload(), UserInfoResponse.class);
        }catch (Exception e){
            return null;
        }
    }

    // Returning Token
    public String currentUser(){
        try {
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

    @Override
    public FileResponse saveFile(MultipartFile file, HttpServletRequest request) throws Exception {
        isNotVerify(UUID.fromString(currentUser()));
        String uploadPath = fileStorageProperties.getUploadPath();
        Path directoryPath = Paths.get(uploadPath).toAbsolutePath().normalize();
        File directory = directoryPath.toFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String fileName = UUID.randomUUID() + file.getOriginalFilename().replaceAll("\\s+","");
        File dest = new File(directoryPath.toFile(), fileName);
        file.transferTo(dest);
        return new FileResponse(fileName,file.getContentType(),file.getSize());
    }

    // Account not yet verify
    public void isNotVerify(UUID id){
        UserRepresentation user = keycloak.realm(realm).users().get(String.valueOf(id)).toRepresentation();
        if(!user.getAttributes().get("is_verify").get(0).equalsIgnoreCase("true")){
            throw new IllegalArgumentException(ValidationConfig.ILLEGAL_USER);
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

    // Validation legal Role
    public void isLegal(UUID id){
        if(!createdBy(id).getLoggedAs().equalsIgnoreCase(String.valueOf(Role.SELLER))){
            throw new IllegalArgumentException(ValidationConfig.ILLEGAL_PROCESS);
        }
    }


}



