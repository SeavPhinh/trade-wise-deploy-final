package com.example.productservice.repository;

import com.example.productservice.model.ProductForSale;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductForSaleRepository extends JpaRepository<ProductForSale, UUID> {

    @Transactional
    @Query(value = "SELECT * FROM product_for_sales WHERE post_id = :#{#id}", nativeQuery = true)
    List<ProductForSale> getProductByPostId(UUID id);

    @Transactional
    @Query(value = "SELECT * FROM product_for_sales WHERE post_id = :#{#id} AND shop_id = :#{#shopId}", nativeQuery = true)
    List<ProductForSale> getProductByPostIdAndUserId(UUID id, UUID shopId);

    @Transactional
    @Query(value = "SELECT * FROM product_for_sales WHERE post_id = :#{#id} LIMIT 1", nativeQuery = true)
    ProductForSale getOneProductByPostId(UUID id);

    @Transactional
    @Query(value = "SELECT * FROM product_for_sales WHERE post_id = :#{#id} AND shop_id = :#{#shopId} LIMIT 1", nativeQuery = true)
    ProductForSale getOneProductByPostIdAndUserId(UUID id, UUID shopId);
}
