package org.rent.app.service.cdc;

import lombok.Data;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CDCProcessingContext
 * <p>
 * Context of WAL scanning in {@link TestDecodingCDCService#processNextCDCChunk() processNextCDCChunk()}
 * </p>
 *
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
@Data
public class CDCProcessingContext {
    private String lastLsn; //the last scanned lsn. It is used to clean WAL
    private long scannedCDCRecords = 0;// number of scanned records. It is used for control.
    private int txCount = 0; // number of scanned transactions. It is used for tuning.
    private String xid = null; // current xid

    private final Matcher tableOperationMatcher;
    private final Matcher columnTypeValueMatcher;

    public CDCProcessingContext(Pattern tableOperationPattern, Pattern columnTypeValuePattern) {
        this.tableOperationMatcher = tableOperationPattern.matcher("");
        this.columnTypeValueMatcher = columnTypeValuePattern.matcher("");
    }

    public void openTransaction(String xid) {
        requireNoOpenTransaction();
        this.xid = Objects.requireNonNull(xid);
        scannedCDCRecords += 1;
    }

    public void addOperation(String xid, TransactionOperation operation) {
        requireOpenTransaction();
        checkXid(xid);
        scannedCDCRecords += 1;
    }

    public void closeTransaction(String xid, String lsn) {
        requireOpenTransaction();
        checkXid(xid);
        this.xid = null;
        lastLsn = lsn;
        txCount += 1;
        scannedCDCRecords += 1;
    }

    public void requireNoOpenTransaction() {
        if (Objects.nonNull(xid)) {
            throw new IllegalStateException("The current transaction is not committed. xid= " + xid);
        }
    }

    private void requireOpenTransaction() {
        if (Objects.isNull(xid)) {
            throw new IllegalStateException("The current transaction is not opened.");
        }
    }

    private void checkXid(String xid) {
        if (!Objects.equals(xid, this.xid)) {
            throw new IllegalStateException("context xid (%s) <> record xid (%s)".formatted(this.xid, xid));
        }
    }
}
