package org.rent.app.service;

import org.rent.app.dto.ProductDto;
import org.rent.app.repository.ProductELKRepository;
import org.rent.app.repository.ProductJPARepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static org.rent.app.service.ProductMapper.dto2jpa;
import static org.rent.app.service.ProductMapper.elk2dto;
import static org.rent.app.service.ProductMapper.jpa2elk;

/**
 * ProductVanillaService
 * <p>
 * A "normal" service to store dto into DB and then into ELK without synchronization.
 * </p>
 *
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
@Service
@Profile("vanilla")
public class ProductVanillaService implements EntityService<ProductDto> {
    @Autowired
    private ProductJPARepository jpaRepository;
    @Autowired
    private ProductELKRepository elkRepository;

    @Override
    @Transactional
    public ProductDto create(ProductDto dto) {
        Objects.requireNonNull(dto).setId(null);
        var dbEntity = jpaRepository.save(dto2jpa(dto));
        return elk2dto(elkRepository.save(jpa2elk(dbEntity)));
    }

    @Override
    @Transactional
    public ProductDto update(ProductDto dto) {
        var jpaEntity = jpaRepository.save(dto2jpa(Objects.requireNonNull(dto)));
        var elkEntity = elkRepository.save(jpa2elk(jpaEntity));
        return elk2dto(elkEntity);
    }

    @Override
    @Transactional
    public void delete(ProductDto dto) {
        var dbEntity = dto2jpa(Objects.requireNonNull(dto));
        jpaRepository.delete(dbEntity);
        elkRepository.delete(jpa2elk(dbEntity));
    }
}
