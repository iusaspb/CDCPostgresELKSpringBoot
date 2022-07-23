package org.rent.app.service;

import org.rent.app.domain.ProductDB;
import org.rent.app.repository.ProductELKRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static org.rent.app.service.ProductMapper.jpa2elk;

/**
 * ProductUncheckedService
 * <p>
 * An implementation of UncheckedEntityService for ProductDB
 * </p>
 *
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
@Service
@Profile("sync")
public class ProductUncheckedService implements UncheckedEntityService<ProductDB> {
    @Autowired
    private ProductELKRepository elkRepository;

    @Override
    public Class<ProductDB> getEntityClass() {
        return ProductDB.class;
    }

    @Override
    public void create(Object jpaEntity) {
        elkRepository.save(jpa2elk((ProductDB) jpaEntity));
    }

    @Override
    public void update(Object jpaEntity) {
        elkRepository.save(jpa2elk((ProductDB) jpaEntity));
    }

    @Override
    public void delete(Object jpaEntity) {
        elkRepository.delete(jpa2elk((ProductDB) jpaEntity));
    }
}
