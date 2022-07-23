package org.rent.app.service;

import org.rent.app.domain.ProductDB;
import org.rent.app.domain.ProductELK;
import org.rent.app.dto.ProductDto;

public class ProductMapper {
    public static ProductDB dto2jpa(ProductDto prod) {
        return ProductDB.builder()
                .id(prod.getId())
                .category(prod.getCategory()).brand(prod.getBrand())
                .name(prod.getName()).description(prod.getDescription())
                .price(prod.getPrice())
                .owner(prod.getOwner())
                .updated(prod.getUpdated())
                .build();
    }

    public static ProductELK jpa2elk(ProductDB prod) {
        return ProductELK.builder()
                .id(prod.getId())
                .category(prod.getCategory()).brand(prod.getBrand())
                .name(prod.getName()).description(prod.getDescription())
                .price(prod.getPrice())
                .owner(prod.getOwner())
                .updated(prod.getUpdated())
                .build();
    }

    public static ProductDto elk2dto(ProductELK prod) {
        return ProductDto.builder()
                .id(prod.getId())
                .category(prod.getCategory()).brand(prod.getBrand())
                .name(prod.getName()).description(prod.getDescription())
                .price(prod.getPrice())
                .owner(prod.getOwner())
                .updated(prod.getUpdated())
                .build();
    }
}
