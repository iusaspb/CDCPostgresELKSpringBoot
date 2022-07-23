package org.rent.app.service;

/**
 * UncheckedEntityService
 * <p>
 * TransactionOperationProcessor.processOp uses instances of this interface
 * to upload JPA data from WAL into the index.
 * Implement this interface for a JPA class if this class  must be in sync with ELK
 *
 * </p>
 *
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
public interface UncheckedEntityService<T> {
    Class<T> getEntityClass();

    void create(Object jpaEntity);

    void update(Object jpaEntity);

    void delete(Object entity);
}
