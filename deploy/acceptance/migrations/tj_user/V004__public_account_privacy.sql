CREATE TABLE user_privacy_consent (
  user_id BIGINT NOT NULL,
  policy_version VARCHAR(64) NOT NULL,
  accepted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(user_id,policy_version)
) ENGINE=InnoDB;
CREATE TABLE user_deletion_request (
  id CHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DEACTIVATED_RETAINED',
  policy_version VARCHAR(64) NOT NULL,
  requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  retain_until DATETIME(3) NOT NULL,
  UNIQUE KEY uq_user_deletion(user_id),
  KEY ix_deletion_retention(status,retain_until)
) ENGINE=InnoDB;
CREATE TABLE user_privacy_request (
  id CHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  scope VARCHAR(32) NOT NULL DEFAULT 'ALL_DOMAINS',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  retained_domains VARCHAR(255) NOT NULL,
  requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reviewed_at DATETIME(3) NULL,
  reviewed_by BIGINT NULL,
  resolution VARCHAR(1000) NULL,
  evidence_reference VARCHAR(255) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  active_kind VARCHAR(16) GENERATED ALWAYS AS (IF(status IN ('PENDING_REVIEW','IN_PROGRESS'),kind,NULL)) STORED,
  UNIQUE KEY uq_privacy_request(user_id,active_kind),
  KEY ix_privacy_pending(status,requested_at)
) ENGINE=InnoDB;
