-- Oracle DDL commits implicitly. Run each statement once with the NC services stopped.
-- After interruption, verify any already-applied statement before continuing with missing statements.
ALTER TABLE qd_es_node ADD (group_scope_id VARCHAR2(64));
ALTER TABLE qd_es_node ADD CONSTRAINT qdes_node_group_scope_fk
  FOREIGN KEY (group_scope_id) REFERENCES qd_es_node (scope_id);
CREATE INDEX qdes_node_group_scope_ix ON qd_es_node (group_scope_id);

CREATE TABLE qd_es_bd_control (
  pk_group VARCHAR2(20) NOT NULL,
  master_enabled CHAR(1) DEFAULT 'N' NOT NULL,
  control_version NUMBER(19) NOT NULL,
  modified_at CHAR(19) NOT NULL,
  CONSTRAINT qdes_bd_control_pk PRIMARY KEY (pk_group),
  CONSTRAINT qdes_bd_control_ck CHECK (master_enabled IN ('Y','N') AND control_version BETWEEN 1 AND 9223372036854775807)
);

CREATE TABLE qd_es_bd_policy (
  pk_group VARCHAR2(20) NOT NULL,
  scope_id VARCHAR2(64) NOT NULL,
  sign_enabled CHAR(1) DEFAULT 'N' NOT NULL,
  reason_required CHAR(1) DEFAULT 'N' NOT NULL,
  audit_enabled CHAR(1) DEFAULT 'N' NOT NULL,
  policy_version NUMBER(19) NOT NULL,
  modified_at CHAR(19) NOT NULL,
  CONSTRAINT qdes_bd_policy_pk PRIMARY KEY (pk_group, scope_id),
  CONSTRAINT qdes_bd_policy_scope_fk FOREIGN KEY (scope_id) REFERENCES qd_es_node (scope_id),
  CONSTRAINT qdes_bd_policy_flags_ck CHECK (sign_enabled IN ('Y','N') AND reason_required IN ('Y','N')
    AND audit_enabled IN ('Y','N') AND policy_version BETWEEN 1 AND 9223372036854775807)
);
CREATE INDEX qdes_bd_policy_scope_ix ON qd_es_bd_policy (scope_id);
