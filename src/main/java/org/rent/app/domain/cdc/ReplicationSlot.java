package org.rent.app.domain.cdc;

import lombok.Getter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Getter
@Entity
@Table(name = "pg_replication_slots")
public class ReplicationSlot {
    @Id
    private String slotName;
    private String plugin;
    private String slotType;
    private String xmin;
    private String catalogXmin;
    private String restartLsn;
    private String confirmedFlushLsn;
}
