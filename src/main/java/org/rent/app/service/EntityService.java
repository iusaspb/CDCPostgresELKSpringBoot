package org.rent.app.service;

/**
 * EntityService
 * <p>
 * Just a basic CRUD interface.
 * ProductVanillaService and ProductCDCService implement it.
 * </p>
 *
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
public interface EntityService<T> {
    T create(T entity);

    T update(T entity);

    void delete(T entity);
}
