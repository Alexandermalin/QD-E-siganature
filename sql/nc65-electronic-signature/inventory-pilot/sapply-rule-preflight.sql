-- Read only. Run after sapply-preflight.sql confirms the PUB_PLUGINITEM columns.
-- A matching row does not prove its ordering, applicability or that the rule
-- has been loaded in every NC service process and participates in the CMT.
WITH bp_points AS (
    SELECT 1 seq,'InsertBP' bp_name,'Insert' rule_name FROM dual UNION ALL
    SELECT 2,'UpdateBP','Update' FROM dual UNION ALL
    SELECT 3,'DeleteBP','Delete' FROM dual UNION ALL
    SELECT 4,'CommitBP','Commit' FROM dual UNION ALL
    SELECT 5,'UnCommitBP','UnCommit' FROM dual UNION ALL
    SELECT 6,'ApproveBP','Approve' FROM dual UNION ALL
    SELECT 7,'UnapproveBP','UnApprove' FROM dual UNION ALL
    SELECT 8,'RowCloseBP','RowClose' FROM dual UNION ALL
    SELECT 9,'RowOpenBP','RowOpen' FROM dual
), phases AS (
    SELECT 1 seq,'before' event_name,'Before' rule_prefix FROM dual UNION ALL
    SELECT 2,'after','After' FROM dual
)
SELECT b.bp_name,p.event_name,
       'nc.impl.ic.qd.esign.SapplyBpRules$'||p.rule_prefix||b.rule_name rule_class,
       COUNT(r.pk_pluginitem) matching_rows,
       SUM(CASE WHEN r.pk_pluginitem IS NOT NULL AND NVL(r.dr,0)=0
                     AND r.vcomponentname='m4455' AND r.veventtype=p.event_name
                     AND r.vextendtype=CASE p.event_name WHEN 'before' THEN 'addBefore' ELSE 'addAfter' END
                     AND r.vtargetclass IS NULL AND r.vextendmodule='ic'
                     AND NVL(r.localtype,'~')='~' AND NVL(r.industrytype,'~')='~'
                THEN 1 ELSE 0 END) matching_candidate_fields,
       CASE WHEN COUNT(r.pk_pluginitem)=0 THEN 'MISSING'
            WHEN COUNT(r.pk_pluginitem)=1 THEN 'PRESENT_REVIEW'
            ELSE 'DUPLICATE' END registration_check
  FROM bp_points b CROSS JOIN phases p
  LEFT JOIN pub_pluginitem r
    ON r.vmodulename='IC'
   AND UPPER(r.vcomponentname)='M4455'
   AND r.vextendpointname='nc.bs.ic.m4455.maintain.'||b.bp_name
   AND LOWER(r.veventtype)=p.event_name
   AND r.vruleclass='nc.impl.ic.qd.esign.SapplyBpRules$'||p.rule_prefix||b.rule_name
 GROUP BY b.seq,b.bp_name,b.rule_name,p.seq,p.event_name,p.rule_prefix
 ORDER BY b.seq,p.seq;
