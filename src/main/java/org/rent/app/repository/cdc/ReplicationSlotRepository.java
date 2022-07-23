package org.rent.app.repository.cdc;

import org.rent.app.domain.cdc.ReplicationSlot;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("sync")
public interface ReplicationSlotRepository extends JpaRepository<ReplicationSlot, String> {
}
