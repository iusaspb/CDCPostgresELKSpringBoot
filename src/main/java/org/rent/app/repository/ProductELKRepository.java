package org.rent.app.repository;

import org.rent.app.domain.ProductELK;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductELKRepository extends ElasticsearchRepository<ProductELK, Long> {
}
