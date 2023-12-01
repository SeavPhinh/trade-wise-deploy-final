package com.example.productservice.controller;

import com.example.commonservice.exception.NotFoundExceptionClass;
import com.example.commonservice.response.ApiResponse;
import com.example.productservice.request.ProductForSaleRequest;
import com.example.productservice.request.ProductForSaleRequestUpdate;
import com.example.productservice.response.ProductForSaleResponse;
import com.example.productservice.service.comment.ProductForSaleService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/product-for-sales")
@Tag(name = "Product For Sales")
@CrossOrigin
public class ProductForSaleController {

    private final ProductForSaleService productForSaleService;
    public static final String POST_SERVICE="postService";

    public ProductForSaleController(ProductForSaleService productForSaleService) {
        this.productForSaleService = productForSaleService;
    }

    @PostMapping("")
    @Operation(summary = "shop adding a product to buyer")
    @SecurityRequirement(name = "oAuth2")
    public ResponseEntity<ApiResponse<ProductForSaleResponse>> addProductToPost(@Valid @RequestBody ProductForSaleRequest postRequest) throws Exception {
        return new ResponseEntity<>(new ApiResponse<>(
                "Shop has added a product to buyer's post successfully",
                productForSaleService.addProductToPost(postRequest),
                HttpStatus.CREATED
        ), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "fetch product for sale by id")
    @SecurityRequirement(name = "oAuth2")
    public ResponseEntity<ApiResponse<ProductForSaleResponse>> getProductForSaleById(@PathVariable UUID id){
        return new ResponseEntity<>(new ApiResponse<>(
                "product for sale fetched by id successfully",
                productForSaleService.getProductById(id),
                HttpStatus.OK
        ), HttpStatus.OK);
    }

    @GetMapping("/post/{id}")
    @Operation(summary = "fetch all product for sale by posted id")
    @SecurityRequirement(name = "oAuth2")
    @CircuitBreaker(name = POST_SERVICE, fallbackMethod = "postUnderMaintenance")
    public ResponseEntity<ApiResponse<List<ProductForSaleResponse>>> getProductForSaleByPostId(@PathVariable UUID id){
        return new ResponseEntity<>(new ApiResponse<>(
                "product fetched by posted id successfully",
                productForSaleService.getProductByPostId(id),
                HttpStatus.OK
        ), HttpStatus.OK);
    }

    @GetMapping("/one/post/{id}")
    @Operation(summary = "fetch one product for sale by posted id")
    @SecurityRequirement(name = "oAuth2")
    @CircuitBreaker(name = POST_SERVICE, fallbackMethod = "postUnderMaintenance")
    public ResponseEntity<ApiResponse<ProductForSaleResponse>> getOneProductForSaleByPostId(@PathVariable UUID id){
        return new ResponseEntity<>(new ApiResponse<>(
                "product fetched by posted id successfully",
                productForSaleService.getOneProductByPostId(id),
                HttpStatus.OK
        ), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "delete product for sale by id")
    @SecurityRequirement(name = "oAuth2")
    public ResponseEntity<ApiResponse<String>> deleteProductForSaleById(@PathVariable UUID id){
        return new ResponseEntity<>(new ApiResponse<>(
                "product delete by id successfully",
                productForSaleService.deleteProductById(id),
                HttpStatus.OK
        ), HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @Operation(summary = "update post by id")
    @SecurityRequirement(name = "oAuth2")
    @CircuitBreaker(name = POST_SERVICE, fallbackMethod = "postUnderMaintenance")
    public ResponseEntity<ApiResponse<ProductForSaleResponse>> updateProductForSaleById(@PathVariable UUID id,
                                                               @Valid @RequestBody ProductForSaleRequestUpdate request) throws Exception {
        return new ResponseEntity<>(new ApiResponse<>(
                " updated product by id successfully",
                productForSaleService.updateProductById(id, request),
                HttpStatus.OK
        ), HttpStatus.OK);
    }

    @PostMapping(value = "/upload/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "upload multiple file")
    @SecurityRequirement(name = "oAuth2")
    public ResponseEntity<?> saveMultiFile(@PathVariable UUID id,
                                           @RequestParam(required = false) List<MultipartFile> files,
                                           HttpServletRequest request) throws IOException {
        if(files != null){
            return ResponseEntity.status(200).body(productForSaleService.saveListFile(id,files,request));
        }
        throw new NotFoundExceptionClass("No filename to upload");
    }

    @GetMapping("/image")
    @Operation(summary = "fetched image")
    @SecurityRequirement(name = "oAuth2")
    public ResponseEntity<ByteArrayResource> getFileByFileName(@RequestParam String fileName) throws IOException {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(productForSaleService.getImage(fileName));
    }
    public ResponseEntity<String> postUnderMaintenance(Exception e) {
        return ResponseEntity.ok("Post service is under maintenance.");
    }

}
