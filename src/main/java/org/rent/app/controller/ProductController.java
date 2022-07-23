package org.rent.app.controller;

import org.rent.app.dto.ProductDto;
import org.rent.app.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("product")
public class ProductController {

    @Autowired
    private EntityService<ProductDto> service;

    @PostMapping()
    public ProductDto create(@RequestBody ProductDto product) {
        return service.create(product);
    }

    @PutMapping
    public ProductDto update(@RequestBody ProductDto product) {
        return service.update(product);
    }

    @DeleteMapping
    public void delete(@RequestBody ProductDto product) {
        service.delete(product);
    }

}
