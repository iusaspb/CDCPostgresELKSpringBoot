package org.rent.app.service.cdc;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * TransactionOperation
 * <p>
 * an operation in the scope of a transaction
 * An instance of this class corresponds to a CDC record of WAL
 * </p>
 *
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
@Data
public class TransactionOperation {
    public enum OperationType {INSERT, UPDATE, DELETE}

    private final OperationType operationType;
    private final String tableName;
    private final Map<String, String> columnValues;
    /*
     * this statement is used for restoring JPA entity from column/values pairs.
     */
    private final String restoreSQLStatement;

    String[] getId(List<String> columns) {

        return columns.stream()
                .map(columnValues::get)
                .toArray(String[]::new);
    }
}
