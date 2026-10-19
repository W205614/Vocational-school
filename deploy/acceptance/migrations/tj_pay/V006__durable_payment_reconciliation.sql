CREATE TABLE provider_payment_reconcile (
  pay_order_no BIGINT PRIMARY KEY,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  lease_token VARCHAR(64),
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expires_at DATETIME(3) NOT NULL,
  last_error VARCHAR(1000),
  replay_reason VARCHAR(2000),
  replayed_by BIGINT,
  replayed_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_provider_reconcile_due(status,next_attempt_at)
) ENGINE=InnoDB;
CREATE TABLE provider_payment_anomaly (
  pay_order_no BIGINT NOT NULL,
  reason VARCHAR(64) NOT NULL,
  expected_amount INT,
  observed_order_no VARCHAR(128),
  observed_amount INT,
  observed_time DATETIME(3),
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  version BIGINT NOT NULL DEFAULT 0,
  resolution VARCHAR(2000),
  resolved_by BIGINT,
  resolved_at DATETIME(3),
  occurrences INT NOT NULL DEFAULT 1,
  first_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(pay_order_no,reason),
  INDEX idx_provider_anomaly_status(status,last_seen_at)
) ENGINE=InnoDB;
