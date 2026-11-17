-- QDNC. Installs the unified database-level bank account lock table.
-- All controlled bank-account writers (save, delete, state) take this row lock
-- after the native partner/account business locks, inside the same CMT.
-- Do not run with autocommit. This script only creates the table; it does not
-- enable any route or policy.

CREATE TABLE qd_es_bankacc_lock (

  -- 银行账户原生主键，也是锁行主键；首次使用时补建。
  pk_bankaccbas VARCHAR2(20) NOT NULL,
  -- 锁行创建 UTC 时间，仅用于观察部署与使用情况。
  created_at CHAR(19) NOT NULL,

  -- 主键保证一个账户一行。
  CONSTRAINT qdes_bankacclock_pk PRIMARY KEY(pk_bankaccbas)
);

COMMENT ON TABLE qd_es_bankacc_lock IS '银行账户数据库级统一锁行；受控保存/删除/启停在同一 CMT 内以 FOR UPDATE 串行化';
