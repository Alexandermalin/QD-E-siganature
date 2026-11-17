-- QDNC. Rolls back the bank account lock table.
-- Lock rows hold no business evidence and can be dropped at any time;
-- run in the stopped maintenance window together with the module patch rollback.
-- Do not run with autocommit.

DROP TABLE qd_es_bankacc_lock;
