package org.rent.app.service.cdc;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.rent.app.domain.cdc.CDCRecord;
import org.rent.app.domain.cdc.ReplicationSlot;
import org.rent.app.repository.cdc.ReplicationSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TestDecodingCDCService
 * <p>
 * Scan WAL with {@code test_decoding} output plugin.
 *
 * @see <a href="https://www.postgresql.org/docs/current/logicaldecoding-output-plugin.html">Logical Decoding Output Plugins</a>
 * </p>
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
@Slf4j
@Service
@Profile("sync")
public class TestDecodingCDCService {
    /*
     *  the slot name was used in
     * SELECT * FROM pg_create_logical_replication_slot('elk_slot', 'test_decoding', false, true);
     */
    private static final String SLOT_NAME = "elk_slot";
    private static final String PLUGIN_NAME = "test_decoding";

    @Autowired
    private ReplicationSlotRepository replicationSlotRepository;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionOperationProcessor processor;

    private static final Pattern columnTypeValuePattern = Pattern.compile(
            "(?<column>[^\\[]+)\\[(?<type>[^]]+)]:(?<value>'[^']*'|[^']\\S*)[\\s]?");
    private static final Pattern tableOperationPattern = Pattern.compile(
            "table\s(?<table>[^:]+):\s(?<operation>[^:]+):\s");
    private static final String COLUMN_DELIM = ", ";

    @PostConstruct
    private void checkReplicationSlot() {
        ReplicationSlot replicationSlot = replicationSlotRepository.findById(SLOT_NAME)
                .orElseThrow(() -> new IllegalStateException("Create replication slot with name [" + SLOT_NAME + "]"));
        if (!PLUGIN_NAME.equals(replicationSlot.getPlugin()))
            throw new IllegalStateException(PLUGIN_NAME);
        if (!"logical".equals(replicationSlot.getSlotType()))
            throw new IllegalStateException("logical");
    }

    ;

    @Transactional(readOnly = true)
    @Async("cdcServiceTaskThreadPoolTaskExecutor")
    public Future<Integer> processNextCDCChunk() {
        CDCProcessingContext context = new CDCProcessingContext(tableOperationPattern, columnTypeValuePattern);
        try (ScrollableResults scroller = em.createNamedQuery("CDCRecord.peekAll")
                .setParameter("slot_name", SLOT_NAME)
                .unwrap(org.hibernate.query.Query.class)
                .scroll(ScrollMode.FORWARD_ONLY)) {
            while (scroller.next()) {
                CDCRecord rawRecord = (CDCRecord) scroller.get()[0];
                processCDCRecord(context, rawRecord);
            }
        }
        context.requireNoOpenTransaction();
        int txCount = context.getTxCount();
        log.debug("Found {} transactions, the last lsn= {}.", txCount, context.getLastLsn());
        // remove processed records from WAL
        long cleanedCDCRecords = removeProcessedCDC(context);
        if (context.getScannedCDCRecords() != cleanedCDCRecords) {
            throw new IllegalStateException(
                    "Something goes wrong. Scanned records (%d) <> cleaned records (%d)"
                            .formatted(context.getScannedCDCRecords(), cleanedCDCRecords));
        }
        return new AsyncResult<>(txCount);
    }

    @Transactional(readOnly = true)
    public long getCDCRecordCount() {
        return em.createNamedQuery("CDCRecord.peekAll", CDCRecord.class)
                .setParameter("slot_name", SLOT_NAME)
                .getResultStream().count();
    }

    /**
     * The record's format depends on using output plugin
     *
     * @param context
     * @param record
     */
    private void processCDCRecord(CDCProcessingContext context, CDCRecord record) {
        String data = record.getData();
        if (data.startsWith("BEGIN ")) {
            // begin transaction record
            String xid = data.substring("BEGIN ".length());
            if (!xid.equals(record.getXid())) {
                throw new IllegalStateException("cdc.xid [%s] <> xid from BEGIN [%s]".formatted(xid, record.getXid()));
            }
            context.openTransaction(xid);
        } else if (data.startsWith("COMMIT ")) {
            // commit transaction record
            String xid = data.substring("COMMIT ".length());
            if (!xid.equals(record.getXid())) {
                throw new IllegalStateException("cdc.xid [%s] <> xid from COMMIT [%s]".formatted(xid, record.getXid()));
            }
            context.closeTransaction(xid, record.getLsn());
        } else if (data.startsWith("table ")) {
            // an operation (INSERT,UPDATE,DELETE) of current transaction record
            TransactionOperation op = parseCDCDataColumn(context, data);
            context.addOperation(record.getXid(), op);
            processor.processOp(op); // upload  WAL data into ELK
        } else {
            throw new IllegalStateException("Unexpected CDCRecord format [" + record + "]");
        }
    }

    /**
     * Parse the data column of CDC record.
     * The format depends on using output plugin
     *
     * @param context - processing context
     * @param data    - the data field of CDC record
     * @return transaction operation
     */
    private TransactionOperation parseCDCDataColumn(CDCProcessingContext context, String data) {
        /*
         * parse a table name and an operation
         */
        Matcher matcher = context.getTableOperationMatcher().reset(data);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Could not find the table name or the operation. data [" + data + "]");
        }
        String tableName = matcher.group("table");
        tableName = tableName.substring(tableName.indexOf('.') + 1);
        TransactionOperation.OperationType operationType;
        /*
         * parse operation
         */
        try {
            operationType = TransactionOperation.OperationType.valueOf(matcher.group("operation"));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unknown operation [" + matcher.group("operation") + "]. data [" + data + "]");
        }
        /*
         * parse columns and values
         */
        matcher = context.getColumnTypeValueMatcher().reset(data.subSequence(matcher.end(), data.length()));
        Map<String, String> columnValueMap = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder("select ");
        while (matcher.find()) {
            String column = matcher.group("column");
            String value = matcher.group("value");
            sb.append(value).append(" as ").append(column).append(COLUMN_DELIM);
            if ('\'' == value.charAt(0)) {
                value = value.substring(1, value.length() - 1);
            }
            columnValueMap.put(column, value);
            log.debug("tx=[{}], table=[{}] column=[{}], type=[{}] value=[{}]", context.getXid(), tableName, column, matcher.group("type"), value);
        }
        if (!matcher.hitEnd()) {
            throw new IllegalStateException("Unexpected tail. data [" + data + "]");
        }
        /*
         * Add columns that are not presented in the record.
         * For instance, only primary keys are presented in DELETE operation.
         * Value for added columns is NULL.
         */
        long count = processor.getNonIdColumns(tableName).stream()
                .filter(x -> !columnValueMap.containsKey(x))
                .peek(column -> sb.append("NULL").append(" as ").append(column).append(COLUMN_DELIM))
                .count();
        // remove the last delimiter from the restore SQL statement string.
        if (!columnValueMap.isEmpty() || count > 0) {
            sb.delete(sb.length() - COLUMN_DELIM.length(), sb.length());
        }
        return new TransactionOperation(operationType, tableName, columnValueMap, sb.toString());
    }

    private long removeProcessedCDC(CDCProcessingContext context) {
        /*
         * get CDC records created before context.getLastLsn().
         * These are exactly the records that were processed during the current call of processNextCDCChunk().
         */
        return em.createNamedQuery("CDCRecord.getProcessed", CDCRecord.class)
                .setParameter("slot_name", SLOT_NAME)
                .setParameter("last_lsn", context.getLastLsn())
                .getResultStream().count();
    }

    ;
}
