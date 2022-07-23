package org.rent.app.service;

import lombok.extern.slf4j.Slf4j;
import org.rent.app.dto.ProductDto;
import org.rent.app.repository.ProductELKRepository;
import org.rent.app.repository.ProductJPARepository;
import org.rent.app.service.cdc.TestDecodingCDCService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.rent.app.service.ProductMapper.dto2jpa;

/**
 * ProductCDCService
 * <p>
 * A version of ProductVanillaService with synchronization.
 * </p>
 *
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
@Slf4j
@Service
@Profile("sync")
public class ProductCDCService implements EntityService<ProductDto> {
    private static long CDC_PROCESSING_TIMEOUT_SEC = 10L;
    @Autowired
    private TestDecodingCDCService cdcService;
    @Autowired
    private ProductJPARepository jpaRepository;
    @Autowired
    private ProductELKRepository elkRepository;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ProductDto create(ProductDto dto) {
        Objects.requireNonNull(dto).setId(null);
        var dbEntity = jpaRepository.save(dto2jpa(dto));
        var id = dbEntity.getId();
        processNextCDCChunk();
        return elkRepository.findById(id).map(ProductMapper::elk2dto).orElse(null);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ProductDto update(ProductDto dto) {
        var id = Objects.requireNonNull(Objects.requireNonNull(dto).getId());
        jpaRepository.save(dto2jpa(dto));
        processNextCDCChunk();
        return elkRepository.findById(id).map(ProductMapper::elk2dto).orElse(null);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void delete(ProductDto dto) {
        var dbEntity = dto2jpa(Objects.requireNonNull(dto));
        jpaRepository.delete(dbEntity);
        processNextCDCChunk();
    }

    public void processNextCDCChunk() {
        try {
            int txCount = cdcService.processNextCDCChunk().get(CDC_PROCESSING_TIMEOUT_SEC, TimeUnit.SECONDS);
            log.debug("Task processed {} transactions", txCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ex) {
            Throwable exx = ex.getCause();
            log.error("Fix an issue and try again processNextCDCChunk() without DB operations.", exx);
            throw new RuntimeException(exx);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Increase timeout and try again processNextCDCChunk() without DB operations.");
        }
    }
}
