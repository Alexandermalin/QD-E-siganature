-- QDNC 1.0.0 only. Run once after reviewing 01_precheck.sql in the maintenance window.
-- Oracle DDL commits implicitly. Verify an existing table against this definition before reusing it.
CREATE TABLE qd_es_org_control (
  pk_group VARCHAR2(20) NOT NULL,
  pk_org VARCHAR2(20) NOT NULL,
  master_enabled CHAR(1) DEFAULT 'N' NOT NULL,
  control_version NUMBER(19) DEFAULT 1 NOT NULL,
  modified_at CHAR(19) NOT NULL,
  CONSTRAINT qdes_org_control_pk PRIMARY KEY (pk_group, pk_org),
  CONSTRAINT qdes_org_control_flag_ck CHECK (master_enabled IN ('Y','N')),
  CONSTRAINT qdes_org_control_ver_ck CHECK (control_version BETWEEN 1 AND 9223372036854775807)
);
