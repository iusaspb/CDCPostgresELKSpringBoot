package org.rent.app;

import org.junit.jupiter.api.Test;
import org.rent.app.dto.ProductDto;
import org.rent.app.repository.ProductELKRepository;
import org.rent.app.repository.ProductJPARepository;
import org.rent.app.service.EntityService;
import org.rent.app.service.cdc.TestDecodingCDCService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.rent.app.service.ProductMapper.dto2jpa;
import static org.rent.app.service.ProductMapper.jpa2elk;

@SpringBootTest(properties = "spring.profiles.active:sync")
public class CRUDTests {
    @Autowired
    private EntityService<ProductDto> productService;
    @Autowired
    private ProductELKRepository productELKRepository;
    @Autowired
    private ProductJPARepository productDBRepository;
    @Autowired
    private TestDecodingCDCService service;

    @Autowired
    private ProductELKRepository elkRepository;

    @Autowired
    private ProductJPARepository dbRepository;

    /*
     * Test plan:
     * 1. create new product;
     * 2. update "name" property of the product;
     * 3. delete the product.
     */
    @Test
    public void crud() {
        ProductDto product = ProductDto.builder()
                .name("prod1")
                .category(2L).brand("brand").description("desc")
                .price(100L)
                .owner(1L)
                .build();
        /*
         *  1. create new product;
         */
        var createdProduct = productService.create(product);
        var id = createdProduct.getId();
        // check that the product with id exists in DB.
        assertNotEquals(Optional.empty(), productDBRepository.findById(id));
        // check that the product with id is as expected.
        productDBRepository.findById(id).ifPresent((actual) ->
                assertThat(dto2jpa(createdProduct)).isEqualToIgnoringGivenFields(
                        actual, "updated"));
        // check that the product with id exists in ELK.
        assertNotEquals(Optional.empty(), productELKRepository.findById(id));
        // check that the product with id is as expected.
        productELKRepository.findById(id).ifPresent((actual) ->
                assertEquals(jpa2elk(dto2jpa(createdProduct)), actual));
        //check that there are no records in WAL.
        assertEquals(0, service.getCDCRecordCount());
        /*
         * 2. update "name" property of the product;
         */
        var updatedProduct = productService.update(createdProduct.toBuilder().name("prod2").build());
        // check that the product with id exists in DB.
        assertNotEquals(Optional.empty(), productDBRepository.findById(id));
        // check that the product with id is as expected.
        productDBRepository.findById(id).ifPresent((actual) ->
                assertThat(dto2jpa(updatedProduct)).isEqualToIgnoringGivenFields(
                        actual, "updated"));
        // check that the product with id exists in ELK.
        assertNotEquals(Optional.empty(), productELKRepository.findById(id));
        // check that the product with id is as expected.
        productELKRepository.findById(id).ifPresent((actual) ->
                assertEquals(jpa2elk(dto2jpa(updatedProduct)), actual));
        //check that there are no records in WAL.
        assertEquals(0, service.getCDCRecordCount());
        /*
         * 3. delete the product.
         */
        var deletedProduct = ProductDto.builder().id(id).build();
        productService.delete(deletedProduct);
        // check that there is no  product with id exists in DB.
        assertEquals(Optional.empty(), productDBRepository.findById(id));
        // check that there is no  product with id exists in ELK.
        assertEquals(Optional.empty(), productELKRepository.findById(id));
        //check that there are no records in WAL.
        assertEquals(0, service.getCDCRecordCount());
    }

    @Test
    public void emptyTest() throws ExecutionException, InterruptedException {
        Integer res = service.processNextCDCChunk().get();
        assertEquals(0, res);
    }
}
