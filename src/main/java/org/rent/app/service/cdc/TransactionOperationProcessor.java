package org.rent.app.service.cdc;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.metamodel.internal.MetamodelImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.SingleTableEntityPersister;
import org.rent.app.service.UncheckedEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import javax.persistence.metamodel.EntityType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * TransactionOperationProcessor
 * <p>
 *  Upload  data from CDC record into ELK
 * </p>
 *
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
@Slf4j
@Service
@Profile("sync")
public class TransactionOperationProcessor {
    record JPAEntityInfo(
            String tableName, // table name of a JPA entity
            Class<?> entityClazz, // java class of an entity
            SingleTableEntityPersister entityPersister, // It is not used at the moment
            List<String> ids, // pk columns
            List<String> nonIds // no pk columns
    ) {
    }

    @PersistenceContext
    private EntityManager em;
    @PersistenceUnit
    private EntityManagerFactory emf;
    @Autowired
    private Collection<UncheckedEntityService<?>> elasticsearchServices;
    /*
     *  contains JPA info hashed by table name
     */
    private Map<String, JPAEntityInfo> jpaEntityInfoByTableName;
    /*
     *  contains ELK services hashed by the JPA entity class
     */
    private Map<Class<?>, UncheckedEntityService<?>> elasticsearchServiceByJPAClass;

    @PostConstruct
    private void init() {
        /*
         * build jpaEntityInfoByTableName
         */
        jpaEntityInfoByTableName = new HashMap<>();
        Set<Class<?>> jpaEntityClazzes = new HashSet<>();
        MetamodelImpl metamodel = (MetamodelImpl) emf.getMetamodel();
        for (EntityType<?> entityType : metamodel.getEntities()) {
            Class<?> entityClazz = entityType.getJavaType();
            EntityPersister generalEntityPersister = metamodel.entityPersister(entityClazz);
            /*
             * below we use specific Hibernate JPA implementation internals
             * as JPA specs does not specify such features
             */
            if (generalEntityPersister instanceof SingleTableEntityPersister entityPersister) {
                JPAEntityInfo entityInfo = new JPAEntityInfo(
                        entityPersister.getTableName(),
                        entityClazz, entityPersister,
                        Collections.unmodifiableList(Arrays.asList(entityPersister.getIdentifierColumnNames())),
                        getNonIdColumns(entityPersister));
                jpaEntityInfoByTableName.put(entityInfo.tableName(), entityInfo);
                jpaEntityClazzes.add(entityClazz);
            } else {
                log.warn("{} does not map to a single table. Skip it.", entityClazz);
            }
        }
        /*
         *  build elasticsearchServiceByJPAClass
         */
        elasticsearchServiceByJPAClass = new HashMap<>();
        elasticsearchServices.forEach(e -> {
            Class<?> clazz = e.getEntityClass();
            if (jpaEntityClazzes.contains(clazz)) {
                elasticsearchServiceByJPAClass.put(clazz, e);
            } else {
                log.warn("Skip {} as there is no entityInfo for it.", clazz);
            }
        });
        /*
         *  skip JPA entities without ELK service
         */
        Set<Class<?>> elkEntityClazzes = elasticsearchServiceByJPAClass.keySet();
        jpaEntityClazzes.removeAll(elkEntityClazzes);
        if (!jpaEntityClazzes.isEmpty()) {
            log.warn("Classes do not have elk service and will be remove from processing: {}", jpaEntityClazzes);
            jpaEntityInfoByTableName.values().removeIf(i -> jpaEntityClazzes.contains(i.entityClazz()));
        }
        log.debug("Supported entities:");
        jpaEntityInfoByTableName.forEach((k, v) -> log.debug("table [{}] class[{}]", k, v.entityClazz()));
    }

    public List<String> getNonIdColumns(String tableName) {
        var entityInfo = jpaEntityInfoByTableName.get(tableName);
        return Objects.nonNull(entityInfo) ? entityInfo.nonIds() : Collections.emptyList();
    }

    /**
     * The main method of the class.
     * It gets an operation from WAL and uploads its data into ELK
     *
     * @param op - WAL operation
     */
    public void processOp(TransactionOperation op) {
        String tableName = op.getTableName();
        // try to find jpaEntityInfo by the table name
        var jpaEntityInfo = jpaEntityInfoByTableName.get(tableName);
        if (Objects.nonNull(jpaEntityInfo)) {
            // get JPA entity class
            Class<?> entityClazz = jpaEntityInfoByTableName.get(tableName).entityClazz;
            // try to find ELK service by JPA entity class
            UncheckedEntityService<?> elkService = findElasticsearchService(entityClazz);
            if (Objects.nonNull(elkService)) {
                /*
                 * restore JPA entity from columns and values of CDC record.
                 * only that part of the entity that is persisted in this table is restored.
                 * JPA properties annotated with @OneToOne, @OneToMany, etc. are not initialized.
                 */
                Object jpaEntity = em.createNativeQuery(op.getRestoreSQLStatement(), entityClazz).getSingleResult();
                log.debug("Restore JPA entity {}", jpaEntity);
                // synch with ELK
                switch (op.getOperationType()) {
                    case INSERT -> elkService.create(jpaEntity);
                    case UPDATE -> elkService.update(jpaEntity);
                    case DELETE -> elkService.delete(jpaEntity);
                    default -> throw new IllegalArgumentException(op.getOperationType().toString());
                }
            } else {
                log.debug("skip TransactionOperation as there is not elkService for table {} enityClass {} ", op.getTableName(), entityClazz);
            }
        } else {
            log.debug("skip TransactionOperation as there is not entityInfo for table {}", op.getTableName());
        }
    }

    private UncheckedEntityService<?> findElasticsearchService(Class<?> jpaClass) {
        UncheckedEntityService<?> res = elasticsearchServiceByJPAClass.get(jpaClass);
        if (Objects.isNull(res))
            for (Map.Entry<Class<?>, UncheckedEntityService<?>> e : elasticsearchServiceByJPAClass.entrySet())
                if (e.getKey().isAssignableFrom(jpaClass)) return e.getValue();
        return res;
    }

    private List<String> getNonIdColumns(SingleTableEntityPersister entityPersister) {
        return IntStream.range(0, entityPersister.getEntityMetamodel().getPropertySpan())
                .mapToObj(idx -> entityPersister.getPropertyColumnNames(idx)[0]).toList();
    }
}
