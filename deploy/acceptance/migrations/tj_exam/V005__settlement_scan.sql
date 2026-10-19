-- A stable keyset cursor replaces the 10000-event settlement ceiling.
-- Apply before deploying either side of the v2 settlement contract.
ALTER TABLE reliability_outbox
 ADD COLUMN settlement_sequence BIGINT NOT NULL AUTO_INCREMENT,
 ADD UNIQUE KEY uq_exam_settlement_sequence(settlement_sequence),
 ADD KEY idx_exam_settlement_scan(routing_key,settlement_sequence);
