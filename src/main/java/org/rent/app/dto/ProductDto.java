package org.rent.app.dto;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private String brand;
    private Long category;
    private Long owner;
    private Long price;
    private LocalDateTime updated;
}
