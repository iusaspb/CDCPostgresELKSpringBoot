package org.rent.app.repository;

import org.rent.app.domain.ProductDB;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJPARepository extends JpaRepository<ProductDB, Long> {
}
