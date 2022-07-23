package org.rent.app.domain.cdc;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.Table;
import java.io.Serializable;

/**
 * CDCRecord
 * <p>
 * WAL record in format of {@code test_decoding} output plugin.
 *
 * @see <a href="https://www.postgresql.org/docs/current/logicaldecoding-output-plugin.html">Logical Decoding Output Plugins</a>
 *
 * </p>
 * @author Sergey Yurkevich ysaspb@gmail.com
 * @since 18.07.2022
 */
@Data
@Entity
@Table(name = "pg_logical_slot_peek_changes")
@NamedNativeQueries({
        @NamedNativeQuery(name = "CDCRecord.peekAll",
                query = "SELECT lsn, xid, data FROM pg_logical_slot_peek_changes(:slot_name, NULL, NULL);",
                resultClass = CDCRecord.class),
        @NamedNativeQuery(name = "CDCRecord.getProcessed",
                query = "SELECT lsn, xid, data FROM pg_logical_slot_get_changes(:slot_name, cast(:last_lsn as pg_lsn), NULL);",
                resultClass = CDCRecord.class)
})
public class CDCRecord implements Serializable {
    /**
     * lsn (Log Sequence Number) data which is a pointer to a location in the WAL
     */
    @Id
    private String lsn;
    /**
     * the transaction id
     */
    @Id
    private String xid;
    /**
     * contains a table name, an operation (INSERT.UPDATE.DELETE) and column/values.
     */
    @Id
    private String data;
}
